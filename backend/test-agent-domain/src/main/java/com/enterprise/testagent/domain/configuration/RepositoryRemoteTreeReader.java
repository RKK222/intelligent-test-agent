package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.user.UserId;
import java.util.List;

/** 版本库远端分支与树的只读端口；实现可复用现有浅克隆缓存，不能物化运行副本。 */
public interface RepositoryRemoteTreeReader {

    List<String> listBranches(CodeRepository repository, UserId userId);

    List<TreeNode> listTree(CodeRepository repository, String branch, UserId userId);

    record TreeNode(String name, String path, String type, List<TreeNode> children) {
        public TreeNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
