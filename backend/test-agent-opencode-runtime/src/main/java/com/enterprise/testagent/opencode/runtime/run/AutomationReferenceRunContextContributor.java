package com.enterprise.testagent.opencode.runtime.run;

import com.enterprise.testagent.agent.runtime.AgentRunPromptContext;
import com.enterprise.testagent.agent.runtime.AgentRunSystemPromptContributor;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceReferenceCatalog.Reference;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 将当前应用激活的自动化代码库以只读 manifest 方式加入本次 Run 上下文。 */
@Component
public class AutomationReferenceRunContextContributor implements AgentRunSystemPromptContributor {

    private final AutomationWorkspaceReferenceCatalog catalog;

    public AutomationReferenceRunContextContributor(AutomationWorkspaceReferenceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Optional<String> contribute(AgentRunPromptContext context) {
        if (context.run().triggeredByUserId() == null) {
            return Optional.empty();
        }
        AutomationWorkspaceReferenceCatalog.Resolution resolution = catalog.resolveActive(
                context.run().triggeredByUserId(), context.run().workspaceId());
        if (resolution.references().isEmpty()) {
            return Optional.empty();
        }
        StringBuilder manifest = new StringBuilder()
                .append("<automation_references readonly=\"true\">\n");
        for (Reference reference : resolution.references()) {
            manifest.append("  <reference>\n")
                    .append("    <name>").append(xml(reference.displayName())).append("</name>\n")
                    .append("    <version>").append(xml(reference.version())).append("</version>\n")
                    .append("    <branch>").append(xml(reference.branch())).append("</branch>\n")
                    .append("    <path>").append(xml(reference.workspaceRootPath())).append("</path>\n")
                    .append("  </reference>\n");
        }
        manifest.append("</automation_references>\n")
                .append("这些目录是当前应用的只读自动化参考代码。按任务需要读取，禁止修改、删除或提交其中内容。");
        return Optional.of(manifest.toString());
    }

    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
