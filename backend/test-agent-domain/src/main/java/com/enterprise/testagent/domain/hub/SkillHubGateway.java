package com.enterprise.testagent.domain.hub;

import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkill;
import com.enterprise.testagent.domain.hub.AgentSkillHubModels.ExternalSkillPackage;
import java.util.List;

/**
 * 外部 SkillHub 端口。认证、HTTP 和 channel 枚举属于 integration 适配器，领域层只接收稳定数据。
 */
public interface SkillHubGateway {

    boolean enabled();

    List<ExternalSkill> listSkills();

    ExternalSkillPackage download(long id, String version);
}
