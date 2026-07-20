package com.enterprise.testagent.xxljob.admin;

import com.enterprise.testagent.xxljob.XxlJobAdminBridge;
import com.enterprise.testagent.xxljob.XxlJobSsoIdentity;
import com.xxl.sso.core.annotation.XxlSso;
import com.xxl.sso.core.helper.XxlSsoHelper;
import com.xxl.sso.core.model.LoginInfo;
import com.xxl.tool.id.UUIDTool;
import com.xxl.tool.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/** 表单 POST 消费平台票据，JIT provision 用户后建立 XXL HttpOnly 会话。 */
@Controller
@RequestMapping("/platform-sso")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformXxlSsoController {

    private final XxlJobAdminBridge bridge;
    private final PlatformXxlJobUserProvisioner provisioner;

    public PlatformXxlSsoController(
            XxlJobAdminBridge bridge,
            PlatformXxlJobUserProvisioner provisioner) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.provisioner = Objects.requireNonNull(provisioner, "provisioner must not be null");
    }

    @PostMapping("/login")
    @XxlSso(login = false)
    public ModelAndView login(
            @RequestParam("ticket") String ticket,
            HttpServletRequest request,
            HttpServletResponse response) {
        Optional<XxlJobSsoIdentity> consumed = bridge.consumeTicket(ticket);
        if (consumed.isEmpty()) {
            return error(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "登录票据无效或已过期，请刷新重试");
        }
        PlatformXxlJobUser user;
        try {
            user = provisioner.provision(consumed.get());
        } catch (RuntimeException exception) {
            return error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "unavailable", "XXL-JOB 管理服务暂不可用");
        }
        LoginInfo loginInfo = new LoginInfo(String.valueOf(user.id()), UUIDTool.getSimpleUUID());
        Response<String> login = XxlSsoHelper.loginWithCookie(loginInfo, response, false);
        if (!login.isSuccess()) {
            return error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "unavailable", "XXL-JOB 登录失败，请刷新重试");
        }
        ModelAndView completed = new ModelAndView("platform/xxl-sso-complete");
        completed.addObject("redirectPath", request.getContextPath() + "/");
        return completed;
    }

    @GetMapping("/required")
    @XxlSso(login = false)
    public ModelAndView required(HttpServletResponse response) {
        return error(response, HttpServletResponse.SC_UNAUTHORIZED, "expired", "平台会话已失效，请重新加载");
    }

    private ModelAndView error(HttpServletResponse response, int status, String state, String message) {
        response.setStatus(status);
        ModelAndView view = new ModelAndView("platform/xxl-sso-status");
        view.addObject("state", state);
        view.addObject("message", message);
        return view;
    }
}
