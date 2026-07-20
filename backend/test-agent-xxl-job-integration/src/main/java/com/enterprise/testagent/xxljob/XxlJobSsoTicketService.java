package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.domain.auth.AuthPrincipal;
import java.util.Optional;

/** 平台 WebFlux API 与 Servlet Admin 共用的一次性票据端口。 */
public interface XxlJobSsoTicketService {

    XxlJobSsoTicketIssue issue(AuthPrincipal principal);

    Optional<XxlJobSsoIdentity> consume(String ticket);
}
