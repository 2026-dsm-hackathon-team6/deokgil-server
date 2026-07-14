package org.example.deokgilserver.common.net;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * 사용자가 입력한 URL을 서버가 직접 호출하기 전에 안전한지 검증한다(SSRF 방어).
 * "URL 형식이 맞다"와 "이 URL이 우리 네트워크 안의 무언가를 가리키지 않는다"는 완전히 다른
 * 질문이다 — scheme/host 존재 여부만 보는 얕은 검사로는 http://169.254.169.254(클라우드 메타데이터),
 * http://127.0.0.1, 사설 대역(10.0.0.0/8 등)을 전혀 걸러내지 못한다.
 *
 * ⚠ 알려진 잔여 위험(DNS rebinding): 여기서 검증에 쓰는 DNS 조회와, 실제로 커넥션을 맺을 때
 * WebClient(Reactor Netty)가 다시 수행하는 DNS 조회는 별개의 시점에 일어난다. 공격자가 짧은
 * TTL로 "검증 시점엔 공개 IP, 연결 시점엔 내부 IP"를 반환하도록 DNS를 조작하면 이 검증을
 * 우회할 수 있다. 완전한 방어는 검증된 IP로 커넥션을 고정(pinning)하면서 TLS SNI는 원래
 * 호스트명으로 보내야 하는데, 이는 Reactor Netty의 resolver/SNI를 별도로 커스터마이징해야
 * 하는 더 큰 작업이라 이번 변경 범위에서는 제외했다 — 후속 작업으로 남겨둔다.
 */
@Component
public class SsrfProtectedUrlValidator {

    private static final int ALLOWED_HTTPS_PORT = 443;

    public URI validate(String rawUrl) {
        URI uri = parse(rawUrl);
        requireHttpsScheme(uri);
        requireNoUserInfo(uri);
        requireHost(uri);
        requireAllowedPort(uri);
        requirePublicAddresses(uri.getHost());
        return uri;
    }

    private URI parse(String rawUrl) {
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
    }

    // http는 지원하지 않는다 — 평문 전송이라 응답이 중간에서 조작될 수 있고(그 내용을
    // 그대로 Claude에 넘긴다는 점에서 위험이 배가된다), SSRF 대상 포트 조합도 더 넓어진다.
    private void requireHttpsScheme(URI uri) {
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
    }

    // user:pass@host 형식은 일부 파서가 host를 다르게 해석하게 만드는 데 악용될 수 있다.
    private void requireNoUserInfo(URI uri) {
        if (uri.getUserInfo() != null) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
    }

    private void requireHost(URI uri) {
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
    }

    // 포트를 임의로 열어두면 방화벽 뒤 내부 서비스(관리 콘솔, DB 등)가 흔히 쓰는 비표준
    // 포트를 스캔당할 표면이 넓어진다. 표준 https 포트(443, 또는 URI에 포트 생략)만 허용한다.
    private void requireAllowedPort(URI uri) {
        int port = uri.getPort();
        if (port != -1 && port != ALLOWED_HTTPS_PORT) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }
    }

    // 호스트명이 가리키는 "모든" IP(getAllByName)를 확인한다 — 라운드로빈 DNS로 여러 IP 중
    // 하나만 내부망이어도 그 레코드를 우회 경로로 쓸 수 있기 때문이다.
    private void requirePublicAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }

        if (addresses.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_URL);
        }

        for (InetAddress address : addresses) {
            if (isPrivateOrReserved(address)) {
                throw new BusinessException(ErrorCode.INVALID_URL);
            }
        }
    }

    private boolean isPrivateOrReserved(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return isCarrierGradeNat(bytes);
        }
        if (bytes.length == 16) {
            return isIpv6UniqueLocal(bytes) || isIpv4MappedPrivate(bytes);
        }
        return false;
    }

    // 100.64.0.0/10 - 통신사 CGNAT 대역. RFC1918은 아니지만 공인망이 아니라서 내부 인프라가
    // 이 대역에 있는 경우가 있다. InetAddress의 기본 판별 메서드들은 이 대역을 걸러주지 않는다.
    private boolean isCarrierGradeNat(byte[] ipv4) {
        int first = ipv4[0] & 0xFF;
        int second = ipv4[1] & 0xFF;
        return first == 100 && second >= 64 && second <= 127;
    }

    // fc00::/7 (Unique Local Address). Java의 Inet6Address.isSiteLocalAddress()는 폐기된
    // fec0::/10 대역만 검사하고 현재 쓰이는 ULA 대역(fc00::/7)은 검사하지 않는 알려진 API 허점이라
    // 별도로 확인해야 한다.
    private boolean isIpv6UniqueLocal(byte[] ipv6) {
        int first = ipv6[0] & 0xFF;
        return (first & 0xFE) == 0xFC;
    }

    // ::ffff:0:0/96 (IPv4-mapped IPv6). 이 형태로 감싸서 IPv4 사설 대역 검사를 우회하려는
    // 시도를 막기 위해, 내부의 IPv4 주소를 꺼내 다시 검사한다.
    private boolean isIpv4MappedPrivate(byte[] ipv6) {
        for (int i = 0; i < 10; i++) {
            if (ipv6[i] != 0) {
                return false;
            }
        }
        if ((ipv6[10] & 0xFF) != 0xFF || (ipv6[11] & 0xFF) != 0xFF) {
            return false;
        }
        try {
            InetAddress embedded = InetAddress.getByAddress(Arrays.copyOfRange(ipv6, 12, 16));
            return isPrivateOrReserved(embedded);
        } catch (UnknownHostException e) {
            // 4바이트를 그대로 주소로 만드는 것뿐이라 사실상 발생하지 않지만, 발생한다면
            // 판단 불가한 입력을 안전 쪽으로(차단) 처리한다.
            return true;
        }
    }
}
