---
aside: false
# 本页的目录、名称、Git 归属和职责统一在这里维护；Vue 组件只负责展示。
directoryMapping:
  defaultExpanded:
    - "root"
    - "ai-agent"
    - "agents"
  repositorySummaries:
    -
      name: "公共 Git"
      content: "跨应用公共 Agent、workagent、Skills、公共规约以及 OpenCode 公共配置；仅超级管理员可维护和发布。"
    -
      name: "应用 Git"
      content: "业务代码、docs、应用 Agent/Skills/规约、archive，以及只在个人分支本地提交的 spec。"
  agentPhysicalSummary: "agents/ = 公共 Git 中的跨应用 Agent/workagent + 应用 Git .opencode/agents 中的应用专属 Agent；页面只做逻辑合并，不产生第三个仓库。"
  implementationStatusSummary: "Agent / workagent / Skill 的实现状态以当前可核验配置为准：已有真实定义标记为“已实现”；尚无对应定义的规划项标记为“未实现”并灰显。"
  directoryTree:
    id: "root"
    name: "应用(服务群组)工作区/"
    scope: "structure"
    note: "开发、测试与个人本地内容的组合视图"
    physical: "2 个 Git 的组合视图"
    children:
      -
        id: "ai-agent"
        name: "ai-agent/"
        scope: "structure"
        note: "公共与应用 AI 配置的逻辑视图"
        physical: "公共 Git + 应用 Git"
        children:
          -
            id: "agents"
            name: "agents/"
            scope: "shared"
            note: "公共基础 Agent/workagent 与应用专属 Agent 的合并目录"
            physical: "公共 Git + 应用 Git"
            children:
              -
                id: "requirements-agent"
                name: "01_需求智能体/"
                scope: "development"
                note: "需求 Agent"
                role: "agent"
                physical: "应用 Git"
              -
                id: "design-agent"
                name: "02_设计智能体/"
                scope: "development"
                note: "设计 Agent"
                role: "agent"
                physical: "应用 Git"
                children:
                  -
                    id: "outline-design"
                    name: "01_概要设计/"
                    scope: "development"
                    note: "概要设计 Agent"
                    role: "agent"
                    physical: "应用 Git"
                    children:
                      -
                        id: "outline-rules"
                        name: "rules/"
                        scope: "shared"
                        note: "公共规约与应用规约"
                        physical: "应用 Git"
                        children:
                          -
                            id: "outline-common-rule"
                            name: "概要设计公共规约.md"
                            scope: "development"
                            note: "开发公共规约"
                            physical: "应用 Git"
                          -
                            id: "outline-app-rule"
                            name: "概要设计应用规约.md"
                            scope: "development"
                            note: "开发应用规约"
                            physical: "应用 Git"
                      -
                        id: "outline-template"
                        name: "template/"
                        scope: "development"
                        note: "模板目录"
                      -
                        id: "outline-agent-file"
                        name: "agent.md"
                        scope: "development"
                        note: "子智能体定义（SOP）"
                      -
                        id: "outline-eval"
                        name: "eval.md"
                        scope: "development"
                        note: "子智能体输出物评估"
                  -
                    id: "detail-design"
                    name: "02_详细设计/"
                    scope: "development"
                    note: "详细设计 Agent"
                    role: "agent"
                    physical: "应用 Git"
                    children:
                      -
                        id: "detail-rules"
                        name: "rules/"
                        scope: "shared"
                        note: "公共规约与应用规约"
                        children:
                          -
                            id: "detail-common-rule"
                            name: "详细设计公共规约.md"
                            scope: "development"
                            note: "开发公共规约"
                            physical: "应用 Git"
                          -
                            id: "detail-app-rule"
                            name: "详细设计应用规约.md"
                            scope: "development"
                            note: "开发应用规约"
                            physical: "应用 Git"
                      -
                        id: "detail-template"
                        name: "template/"
                        scope: "development"
                        note: "模板目录"
                      -
                        id: "detail-agent-file"
                        name: "agent.md"
                        scope: "development"
                        note: "子智能体定义（SOP）"
                      -
                        id: "detail-eval"
                        name: "eval.md"
                        scope: "development"
                        note: "子智能体输出物评估"
                  -
                    id: "program-design"
                    name: "03_程序设计/"
                    scope: "development"
                    note: "程序设计 Agent"
                    role: "agent"
                    physical: "应用 Git"
                    children:
                      -
                        id: "program-rules"
                        name: "rules/"
                        scope: "shared"
                        note: "公共规约与应用规约"
                        children:
                          -
                            id: "program-common-rule"
                            name: "程序设计公共规约.md"
                            scope: "development"
                            note: "开发公共规约"
                            physical: "应用 Git"
                          -
                            id: "program-app-rule"
                            name: "程序设计应用规约.md"
                            scope: "development"
                            note: "开发应用规约"
                            physical: "应用 Git"
                      -
                        id: "program-template"
                        name: "template/"
                        scope: "development"
                        note: "模板目录"
                      -
                        id: "program-agent-file"
                        name: "agent.md"
                        scope: "development"
                        note: "子智能体定义（SOP）"
                      -
                        id: "program-eval"
                        name: "evaluation.md"
                        scope: "development"
                        note: "子智能体输出物评估"
              -
                id: "coding-agent"
                name: "03_编码智能体/"
                scope: "development"
                note: "编码 Agent"
                role: "agent"
                physical: "应用 Git"
              -
                id: "testing-agent"
                name: "04_测试智能体/"
                scope: "testing"
                note: "测试 Agent 与 workagent 分类"
                physical: "公共 Git + 应用 Git"
                children:
                  -
                    id: "test-design-agent"
                    name: "01_测试设计/"
                    scope: "testing"
                    note: "用户可 @ 的测试设计入口"
                    role: "agent"
                    physical: "公共 Git + 应用 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "test-design-agent-file"
                        name: "test-design-orchestrator.md"
                        scope: "testing"
                        note: "Test Design（测试设计）Agent 定义"
                        physical: "公共 Git"
                      -
                        id: "application-test-design-agent"
                        name: "<应用测试设计 Agent>.md"
                        scope: "testing"
                        note: "当前测试设计活动的应用专属入口占位"
                        role: "agent"
                        physical: "应用 Git"
                        implementation: "planned"
                      -
                        id: "test-design-rules"
                        name: "rules/"
                        scope: "testing"
                        note: "测试公共规约与应用测试规约"
                        children:
                          -
                            id: "test-design-common-rules"
                            name: "测试设计公共规约/"
                            scope: "testing"
                            note: "公共规约"
                            physical: "公共 Git"
                            children:
                              -
                                id: "test-design-api-rule"
                                name: "接口测试设计规约.md"
                                scope: "testing"
                              -
                                id: "test-design-ui-rule"
                                name: "UI测试设计规约.md"
                                scope: "testing"
                              -
                                id: "test-design-async-rule"
                                name: "异步任务测试设计规约.md"
                                scope: "testing"
                              -
                                id: "test-design-batch-rule"
                                name: "批量任务测试设计规约.md"
                                scope: "testing"
                              -
                                id: "test-design-other-rule"
                                name: "其他测试设计规约.md"
                                scope: "testing"
                          -
                            id: "test-design-app-rules"
                            name: "测试设计应用规约/"
                            scope: "testing"
                            note: "按测试对象类型维护应用规约"
                            physical: "应用 Git"
                            children:
                              -
                                id: "test-design-app-api-rule"
                                name: "接口测试设计应用规约.md"
                                scope: "testing"
                              -
                                id: "test-design-app-ui-rule"
                                name: "UI测试设计应用规约.md"
                                scope: "testing"
                              -
                                id: "test-design-app-async-rule"
                                name: "异步任务测试设计应用规约.md"
                                scope: "testing"
                              -
                                id: "test-design-app-batch-rule"
                                name: "批量任务测试设计应用规约.md"
                                scope: "testing"
                              -
                                id: "test-design-app-other-rule"
                                name: "其他测试设计应用规约.md"
                                scope: "testing"
                      -
                        id: "test-analysis-workagent"
                        name: "001 Test Analysis（测试分析）/"
                        scope: "testing"
                        note: "供测试设计 Agent 调用"
                        role: "workagent"
                        physical: "公共 Git"
                        implementation: "implemented"
                        children:
                          -
                            id: "test-analysis-workagent-file"
                            name: "test-design-analysis.md"
                            scope: "testing"
                      -
                        id: "test-generation-workagent"
                        name: "002 Test Case Generation（测试案例生成）/"
                        scope: "testing"
                        note: "供测试设计 Agent 调用"
                        role: "workagent"
                        physical: "公共 Git"
                        implementation: "implemented"
                        children:
                          -
                            id: "test-generation-workagent-file"
                            name: "test-design-generation.md"
                            scope: "testing"
                      -
                        id: "test-review-workagent"
                        name: "003 Test Case Review（测试案例审核）/"
                        scope: "testing"
                        note: "供测试设计 Agent 调用"
                        role: "workagent"
                        physical: "公共 Git"
                        implementation: "implemented"
                        children:
                          -
                            id: "test-review-workagent-file"
                            name: "test-design-review.md"
                            scope: "testing"
                      -
                        id: "application-test-design-workagent"
                        name: "<应用测试设计 workagent>.md"
                        scope: "testing"
                        note: "当前测试设计活动的应用专属工作单元占位"
                        role: "workagent"
                        physical: "应用 Git"
                        implementation: "planned"
                  -
                    id: "test-execution-agent"
                    name: "02_测试执行/"
                    scope: "testing"
                    note: "用户可 @ 的测试执行入口"
                    role: "agent"
                    physical: "公共 Git + 应用 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "test-execution-agent-file"
                        name: "test-execution-agent.md"
                        scope: "testing"
                        note: "Test Execution（测试执行）Agent 定义"
                        physical: "公共 Git"
                      -
                        id: "application-test-execution-agent"
                        name: "<应用测试执行 Agent>.md"
                        scope: "testing"
                        note: "当前测试执行活动的应用专属入口占位"
                        role: "agent"
                        physical: "应用 Git"
                        implementation: "planned"
                      -
                        id: "test-execution-rules"
                        name: "rules/"
                        scope: "testing"
                        note: "测试公共规约与应用测试规约"
                        children:
                          -
                            id: "test-execution-common-rules"
                            name: "测试执行公共规约/"
                            scope: "testing"
                            note: "公共规约"
                            physical: "公共 Git"
                            children:
                              -
                                id: "test-execution-api-rule"
                                name: "接口测试执行规约.md"
                                scope: "testing"
                              -
                                id: "test-execution-ui-rule"
                                name: "UI测试执行规约.md"
                                scope: "testing"
                              -
                                id: "test-execution-more-rule"
                                name: "..."
                                scope: "testing"
                                note: "其他执行规约"
                          -
                            id: "test-execution-app-rule"
                            name: "测试执行应用规约.md"
                            scope: "testing"
                            note: "应用规约"
                            physical: "应用 Git"
                      -
                        id: "api-execution-workagent"
                        name: "001 API Test Execution（接口测试执行）/"
                        scope: "testing"
                        note: "供测试执行 Agent 调用"
                        role: "workagent"
                        physical: "公共 Git"
                        implementation: "implemented"
                        children:
                          -
                            id: "api-execution-workagent-file"
                            name: "test-execution-api.md"
                            scope: "testing"
                      -
                        id: "application-test-execution-workagent"
                        name: "<应用测试执行 workagent>.md"
                        scope: "testing"
                        note: "当前测试执行活动的应用专属工作单元占位"
                        role: "workagent"
                        physical: "应用 Git"
                        implementation: "planned"
          -
            id: "mcp"
            name: "mcp/"
            scope: "shared"
            note: "开发与测试运行配置"
            physical: "公共 Git + 应用 Git"
          -
            id: "engineering-rules"
            name: "rules/"
            scope: "shared"
            note: "开发规约、测试公共规约与应用测试规约"
            physical: "公共 Git + 应用 Git"
            children:
              -
                id: "engineering-rule"
                name: "工程规约.md"
                scope: "shared"
                note: "按适用范围归属对应 Git"
          -
            id: "skills"
            name: "skills/"
            scope: "shared"
            note: "开发、测试公共与应用测试 Skill 的逻辑合并目录"
            physical: "公共 Git + 应用 Git"
            children:
              -
                id: "coding-skills"
                name: "coding/"
                scope: "development"
                note: "开发 Skill"
                physical: "应用 Git"
                children:
                  -
                    id: "code-review-skill"
                    name: "code-review-skill/"
                    scope: "development"
                    implementation: "planned"
                    children:
                      -
                        id: "code-review-skill-file"
                        name: "SKILL.md"
                        scope: "development"
                        implementation: "planned"
              -
                id: "test-skills"
                name: "test/"
                scope: "testing"
                note: "测试公共与应用测试 Skill"
                physical: "公共 Git + 应用 Git"
                children:
                  -
                    id: "test-design-skill"
                    name: "test-design/"
                    scope: "testing"
                    note: "Test Design（测试设计）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "test-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "api-design-skill"
                    name: "test-design-api/"
                    scope: "testing"
                    note: "API Testing（接口法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "api-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "augment-design-skill"
                    name: "test-design-augment/"
                    scope: "testing"
                    note: "Case Augmentation（增补法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "augment-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "direct-design-skill"
                    name: "test-design-direct/"
                    scope: "testing"
                    note: "Rule-based Testing（规则法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "direct-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "equivalence-skill"
                    name: "test-design-equivalence/"
                    scope: "testing"
                    note: "Equivalence Partitioning（等价类法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "equivalence-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "orthogonal-design-skill"
                    name: "test-design-orthogonal/"
                    scope: "testing"
                    note: "Orthogonal Array（正交法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "orthogonal-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "path-design-skill"
                    name: "test-design-path/"
                    scope: "testing"
                    note: "Path Testing（路径法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "path-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "scenario-design-skill"
                    name: "test-design-scenario/"
                    scope: "testing"
                    note: "Scenario Testing（场景法）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "scenario-design-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "api-case-execution-skill"
                    name: "api-execute-case/"
                    scope: "testing"
                    note: "API Case Execution（接口案例执行）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "api-case-execution-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "api-automation-skill"
                    name: "generate-api-automation-markdown/"
                    scope: "testing"
                    note: "API Automation Script（接口自动化脚本）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "api-automation-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "test-message-skill"
                    name: "generate-test-messages/"
                    scope: "testing"
                    note: "Test Message Generation（测试报文生成）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "test-message-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "automation-format-skill"
                    name: "validate-automation-script-format/"
                    scope: "testing"
                    note: "Automation Format Check（自动化格式检查）"
                    physical: "公共 Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "automation-format-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                  -
                    id: "application-test-skills"
                    name: "<应用专属测试 Skill>/"
                    scope: "testing"
                    note: "应用测试方法或知识"
                    physical: "应用 Git"
                    implementation: "planned"
                    children:
                      -
                        id: "application-test-skill-file"
                        name: "SKILL.md"
                        scope: "testing"
                        implementation: "planned"
      -
        id: "archive"
        name: "archive/"
        scope: "shared"
        note: "开发与测试共用的评审归档"
        physical: "应用 Git"
        children:
          -
            id: "archive-period"
            name: "2601/"
            scope: "shared"
            note: "归档年月"
            children:
              -
                id: "archive-item"
                name: "I000001/"
                scope: "shared"
                note: "需求项编号"
      -
        id: "spec"
        name: "spec/"
        scope: "local"
        note: "个人本地规格工作目录，只提交不推送"
        physical: "应用 Git 个人分支 · 仅本地提交"
        children:
          -
            id: "spec-item"
            name: "I000001/"
            scope: "local"
            note: "需求项编号"
            children:
              -
                id: "requirements-spec"
                name: "01-需求/"
                scope: "local"
                children:
                  -
                    id: "requirements-use-case"
                    name: "1_需求用例.md"
                    scope: "local"
              -
                id: "design-spec"
                name: "02-设计/"
                scope: "local"
                children:
                  -
                    id: "outline-spec"
                    name: "01.概要设计.md"
                    scope: "local"
                  -
                    id: "subitem-spec"
                    name: "S000001/"
                    scope: "local"
                    note: "需求子条目编号"
                    children:
                      -
                        id: "detail-spec"
                        name: "xxx_详细设计.md"
                        scope: "local"
                      -
                        id: "program-spec"
                        name: "xxx_程序设计.md"
                        scope: "local"
              -
                id: "coding-spec"
                name: "03-编码/"
                scope: "local"
                children:
                  -
                    id: "business-code"
                    name: "业务代码/"
                    scope: "local"
                  -
                    id: "unit-test"
                    name: "单元测试/"
                    scope: "local"
              -
                id: "testing-spec"
                name: "04-测试/"
                scope: "local"
                children:
                  -
                    id: "test-design-spec"
                    name: "测试设计/"
                    scope: "local"
                    children:
                      -
                        id: "flow-test-design"
                        name: "流程测试/"
                        scope: "local"
                        children:
                          -
                            id: "flow-design-docs"
                            name: "测试设计文档/"
                            scope: "local"
                            children:
                              -
                                id: "flow-design-doc"
                                name: "流程测试设计.md"
                                scope: "local"
                          -
                            id: "flow-test-cases"
                            name: "测试案例/"
                            scope: "local"
                            children:
                              -
                                id: "flow-test-case"
                                name: "流程测试案例.md"
                                scope: "local"
                      -
                        id: "subitem-test-design"
                        name: "S000001/"
                        scope: "local"
                        children:
                          -
                            id: "subitem-design-docs"
                            name: "测试设计文档/"
                            scope: "local"
                            children:
                              -
                                id: "subitem-design-doc"
                                name: "S000001_测试设计.md"
                                scope: "local"
                          -
                            id: "subitem-test-cases"
                            name: "测试案例/"
                            scope: "local"
                            children:
                              -
                                id: "subitem-test-case"
                                name: "S000001_测试案例.md"
                                scope: "local"
                  -
                    id: "test-execution-spec"
                    name: "测试执行/"
                    scope: "local"
                    children:
                      -
                        id: "flow-test-execution"
                        name: "流程测试/"
                        scope: "local"
                        children:
                          -
                            id: "flow-test-data"
                            name: "测试数据.md"
                            scope: "local"
                          -
                            id: "flow-test-script"
                            name: "测试脚本.md"
                            scope: "local"
                      -
                        id: "subitem-test-execution"
                        name: "S000001/"
                        scope: "local"
                        children:
                          -
                            id: "subitem-test-data"
                            name: "测试数据.md"
                            scope: "local"
                          -
                            id: "subitem-test-script"
                            name: "测试脚本.md"
                            scope: "local"
      -
        id: "docs"
        name: "docs/"
        scope: "shared"
        note: "开发资产与测试资产的应用级合并目录"
        physical: "应用 Git"
        children:
          -
            id: "application-architecture"
            name: "应用架构/"
            scope: "shared"
            note: "开发应用关系与场景测试资产共用目录"
            physical: "应用 Git"
            children:
              -
                id: "application-architecture-file"
                name: "应用架构.md"
                scope: "development"
                note: "应用关系、服务节点和功能模块清单"
                physical: "应用 Git"
              -
                id: "test-overview"
                name: "测试概述.md"
                scope: "testing"
                note: "目录索引、非功能要求、公共案例和环境"
                physical: "应用 Git"
              -
                id: "application-scenario-x"
                name: "应用场景说明书_XXX.md"
                scope: "testing"
                note: "场景图、业务规则、核心要素和测试关注点"
                physical: "应用 Git"
              -
                id: "application-scenario-y"
                name: "应用场景说明书_YYY.md"
                scope: "testing"
                physical: "应用 Git"
              -
                id: "application-scenario-more"
                name: "..."
                scope: "testing"
                note: "其他应用场景说明书"
                physical: "应用 Git"
          -
            id: "technical-architecture"
            name: "技术架构/"
            scope: "development"
            note: "开发工程与技术栈概览"
            physical: "应用 Git"
            children:
              -
                id: "engineering-overview-a"
                name: "工程概览_A.md"
                scope: "development"
                note: "技术栈、接口清单和应用关系"
                physical: "应用 Git"
              -
                id: "engineering-overview-b"
                name: "工程概览_B.md"
                scope: "development"
                note: "技术栈、接口清单和应用关系"
                physical: "应用 Git"
              -
                id: "engineering-overview-more"
                name: "..."
                scope: "development"
                note: "其他工程概览"
                physical: "应用 Git"
          -
            id: "functional-module"
            name: "功能模块/"
            scope: "shared"
            note: "开发功能说明与测试设计案例共用目录"
            physical: "应用 Git"
            children:
              -
                id: "functional-module-x"
                name: "功能模块_XXX.md"
                scope: "development"
                note: "业务说明书"
                physical: "应用 Git"
              -
                id: "functional-module-y"
                name: "功能模块_YYY.md"
                scope: "development"
                note: "业务说明书"
                physical: "应用 Git"
              -
                id: "functional-design-x1"
                name: "测试设计文档_X1.md"
                scope: "testing"
                note: "等价类表、路径图等测试设计"
                physical: "应用 Git"
              -
                id: "functional-design-x2"
                name: "测试设计文档_X2.md"
                scope: "testing"
                physical: "应用 Git"
              -
                id: "functional-case-x1"
                name: "测试案例_X1.md"
                scope: "testing"
                physical: "应用 Git"
              -
                id: "functional-case-x2"
                name: "测试案例_X2.md"
                scope: "testing"
                physical: "应用 Git"
              -
                id: "functional-more"
                name: "..."
                scope: "testing"
                note: "其他功能测试资产"
                physical: "应用 Git"
          -
            id: "functional-docs"
            name: "功能文档/"
            scope: "development"
            note: "开发功能文档"
            physical: "应用 Git"
            children:
              -
                id: "functional-doc-x1"
                name: "功能文档_X1.md"
                scope: "development"
              -
                id: "functional-doc-x2"
                name: "功能文档_X2.md"
                scope: "development"
              -
                id: "functional-doc-more"
                name: "..."
                scope: "development"
                note: "其他功能文档"
          -
            id: "data-architecture"
            name: "数据架构/"
            scope: "shared"
            note: "开发数据库结构与测试数据实体共用目录"
            physical: "应用 Git"
            children:
              -
                id: "gauss-schema"
                name: "F-ABC_Gauss_1.yaml"
                scope: "development"
                note: "开发数据架构资产"
                physical: "应用 Git"
              -
                id: "mysql-schema"
                name: "F-ABC_MySQL_1.yaml"
                scope: "development"
                note: "开发数据架构资产"
                physical: "应用 Git"
              -
                id: "data-entity-x1"
                name: "数据实体_X1.md"
                scope: "testing"
                physical: "应用 Git"
              -
                id: "data-entity-x2"
                name: "数据实体_X2.md"
                scope: "testing"
                physical: "应用 Git"
          -
            id: "business-knowledge"
            name: "业务知识/"
            scope: "development"
            note: "开发业务知识资产"
            physical: "应用 Git"
            children:
              -
                id: "business-knowledge-more"
                name: "..."
                scope: "development"
                note: "领域术语、规则和业务说明"
          -
            id: "deployment-architecture"
            name: "部署架构/"
            scope: "testing"
            note: "测试新增架构资产"
            physical: "应用 Git"
            children:
              -
                id: "physical-deployment"
                name: "物理部署架构"
                scope: "testing"
      -
        id: "business-repo-a"
        name: "git-repo-A/"
        scope: "development"
        note: "业务 Git 工程 A"
        physical: "应用 Git"
        children:
          -
            id: "business-repo-a-docs"
            name: "docs/"
            scope: "development"
            children:
              -
                id: "shared-methods"
                name: "公共方法/"
                scope: "development"
      -
        id: "business-repo-b"
        name: "git-repo-B/"
        scope: "development"
        note: "业务 Git 工程 B"
        physical: "应用 Git"
  agentDesignSummary: "workagent 可以独立完成任务，但主要供上层 Agent 编排调用；OpenCode 技术实现为 mode: subagent、hidden: true，不建议用户单独调用。"
  agentDesignRows:
    -
      scope: "跨应用公共能力"
      role: "Agent / workagent / Skill"
      description: "物理文件位于公共 Git；只有超级管理员可以创建 worktree、修改、提交和推送。"
    -
      scope: "应用专属能力"
      role: "Agent / Skill / rules / templates"
      description: "物理文件位于应用 Git 的 .opencode/；只有应用管理员和超级管理员可以维护和发布。"
    -
      scope: "应用稳定文档"
      role: "docs/**"
      description: "物理文件位于应用 Git；所有应用成员都可在个人 worktree 中维护，并按 feature 投影流程发布。"
    -
      scope: "个人过程资产"
      role: "spec/**"
      description: "物理文件位于应用 Git 的个人分支；所有应用成员都可本地提交，普通成员和应用管理员不能发布，超级管理员可发布。"
  ownershipRows:
    -
      directory: "公共 Agent / workagent / Skills / 规约"
      content: "跨应用通用智能体、工作单元、方法和统一质量要求"
      builders: "公共能力建设团队"
      publisher: "平台超级管理员"
    -
      directory: "应用 Agent / Skills / rules / templates"
      content: "结合应用特点维护的智能体、技能、规约和模板"
      builders: "应用团队"
      publisher: "应用管理员及以上"
    -
      directory: "docs/**"
      content: "开发与测试稳定资产的应用级沉淀"
      builders: "所有应用成员"
      publisher: "所有应用成员可按个人 HEAD 投影发布"
    -
      directory: "spec/<需求项>"
      content: "具体研发阶段的个人输入输出产物"
      builders: "当前用户与 Agent"
      publisher: "所有角色仅本地提交，禁止发布"
    -
      directory: "archive/** 与其它应用文件"
      content: "应用业务代码、单测、归档和工程文件"
      builders: "所有应用成员"
      publisher: "按个人 HEAD 白名单投影到 feature 分支"
  responsibilityRules:
    -
      index: "01"
      title: "公共 Git 超级管理员负责"
      description: "非超级管理员前端只读，后端同样拒绝公共 Git 写操作。"
    -
      index: "02"
      title: "应用配置由应用管理员负责"
      description: "应用 Agent、Skill、rules 和 templates 对普通成员只读。"
    -
      index: "03"
      title: "docs 可发布，spec 仅本地"
      description: "所有应用成员均可发布 docs；spec 可以本地提交，但任何角色都不能推送。"
---

# 开发与测试目录

本页按当前已经落地的工作区和权限实现说明目录归属。页面把公共能力、应用能力和个人过程资产组合成一棵工程树，但物理上只有公共 Git 与应用 Git，不会为逻辑目录再创建第三套仓库。

> 后续调整目录、Agent/workagent/Skill 名称、Git 归属或建设职责时，只修改本文件顶部的 `directoryMapping` 数据；`DirectoryMapping.vue` 仅负责通用展示和展开交互。

<DirectoryMapping />

## 两套 Git 的物理边界

| 物理 Git | 当前内容 | 写入与发布权限 |
| --- | --- | --- |
| 公共 Git | `opencode/agents/**`、`opencode/skills/**`、公共规约和公共 OpenCode 配置 | 仅超级管理员；其他角色只读 |
| 应用 Git | 业务文件、`docs/**`、`archive/**`、`.opencode/**`，以及个人分支中的 `spec/**` | 普通文件和 `docs/**` 所有应用成员可维护；应用配置仅应用管理员及以上；任何角色都禁止发布 `spec/**` |

没有对应权限时，左侧 Agents 配置树不提供创建或发布入口，Agent/Skill 文件以只读编辑器打开；后端文件通道与 Git API 仍会校验所有写入、暂存和发布请求，不能通过直接请求绕过。

## 个人 worktree 与 OpenCode

每位用户在应用版本下都有独立的个人 worktree，固定保存在该用户 TestAgent 进程所属服务器的磁盘中。OpenCode、文件树、编辑器和终端始终读取这个个人 worktree，所以同一会话看到的是同一套绝对路径与文件名。

应用 feature worktree 是同一个应用 Git 的另一个物理 worktree。发布时平台根据个人工作区记录定位个人仓库，从个人 `HEAD` 按仓库相对路径读取已提交文件，再把允许发布的文件投影到 feature worktree；两个 worktree 绝对路径不同，但仓库相对文件名一致。

## 目录与发布规则

- `.opencode/agents/**` 与 `.opencode/skills/**`（包括 `rules/`、`templates/`）由应用管理员或超级管理员维护。创建应用配置包会生成 OpenCode 可识别的 Agent、`SKILL.md`、rules 和 templates 模板。
- `docs/**` 是应用 Git 中的共享稳定文档，所有应用成员都可以编辑、本地提交并通过 feature 投影发布。
- `spec/**` 是个人过程资产，所有角色都可以编辑、暂存和提交到个人分支，但任何角色都不能发布，超级管理员也不能绕过目录限制。生成结果按平台现有流程另行同步到系统。
- `archive/**`、业务代码、单元测试和其它普通应用文件沿用个人 worktree 的提交与 feature 投影流程。

## 从个人 HEAD 发布到 feature

1. 用户在个人 worktree 中编辑并暂存所需文件。
2. “提交”或“提交并推送”先把选中文件提交到个人 `HEAD`。
3. 选择推送时，后端只读取已提交且允许发布的白名单路径，不 merge 或 push 整个个人分支。
4. 后端把这些相对路径投影到应用 feature worktree，在 feature 分支提交并推送。
5. 推送成功后平台广播版本同步通知；其他在线用户手动刷新或同步，系统不会自动覆盖脏工作树。

任意角色一次选择同时包含 `spec/**` 和可发布文件时，`spec/**` 只进入个人提交，其它文件继续发布；如果只选择 `spec/**`，“提交并推送”不可用，只能执行本地“提交”。后端会拒绝所有角色通过直接 API 或 `./spec/**` 路径别名发布 spec，旧同步接口同样维持禁止。
