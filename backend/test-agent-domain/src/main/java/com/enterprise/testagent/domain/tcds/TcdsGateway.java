package com.enterprise.testagent.domain.tcds;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * TCDS 领域端口。业务模块只消费脱敏后的领域模型，不感知 token、HTTP 路径或响应包络。
 */
public interface TcdsGateway {

    /** 按统一认证号查询用户资料；认证登录允许在 TCDS 不可用时按既有逻辑降级。 */
    Optional<UserProfile> findUser(String unifiedAuthId);

    /** 查询 TCDS 按当前用户返回的应用目录；调用方可将其作为输入建议。 */
    List<Application> listApplications(String unifiedAuthId);

    /** 按应用和版本查询需求父条目及子条目。 */
    List<RequirementItem> listRequirementItems(String unifiedAuthId, String appShortName, String editionId);

    /** 当子条目没有 fileType=1 的设计文档时，查询 TCDS 的设计文档兜底接口。 */
    Optional<Document> findFallbackDesignDocument(String unifiedAuthId, String subItemNo);

    /** 下载只能来自本端口查询结果的文档；实现必须执行协议、超时和字节上限校验。 */
    DownloadedDocument download(Document document, long maxBytes);

    record UserProfile(String fullName, String loginName, String rdDepartment, String department) {
    }

    record Application(String appName, String appShortName) {
    }

    record RequirementItem(String itemNo, String itemName, List<RequirementSubItem> children) {
        public RequirementItem {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    record RequirementSubItem(String itemNo, String itemName, List<Document> documents) {
        public RequirementSubItem {
            documents = documents == null ? List.of() : List.copyOf(documents);
        }
    }

    /** 文档地址只由 TCDS 适配器构造，浏览器请求模型不包含该字段。 */
    record Document(String fileName, URI uri, String fileType) {
    }

    record DownloadedDocument(byte[] content, String contentType) {
        public DownloadedDocument {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
