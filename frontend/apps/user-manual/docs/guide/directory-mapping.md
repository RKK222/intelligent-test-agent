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
      name: "开发 AI Git"
      content: "全部开发 Agent / Skills / 规约 / docs 资产。"
    -
      name: "测试公共 AI Git"
      content: "跨应用测试 Agent / workagent / Skills / 公共规约。"
    -
      name: "测试 AI Git"
      content: "应用测试 Agent / Skills / 应用规约 / docs 测试资产 / archive / spec。"
    -
      name: "开发业务代码 Git"
      content: "业务代码、单元测试和工程文档。"
  agentPhysicalSummary: "agents/ = 开发 AI Git 的开发 Agent + 测试公共 AI Git 的公共测试 Agent/workagent + 测试 AI Git 的应用测试 Agent/workagent。"
  implementationStatusSummary: "Agent / workagent / Skill 的实现状态以当前可核验配置为准：测试公共 AI Git 已存在的真实定义标记为“已实现”；尚无对应定义的规划项标记为“未实现”并灰显。"
  directoryTree:
    id: "root"
    name: "应用(服务群组)工作区/"
    scope: "structure"
    note: "开发、测试与个人本地内容的组合视图"
    physical: "4 个 Git 的组合视图"
    children:
      -
        id: "ai-agent"
        name: "ai-agent/"
        scope: "structure"
        note: "公共与应用 AI 配置的逻辑视图"
        physical: "开发 AI Git + 测试公共 AI Git + 测试 AI Git"
        children:
          -
            id: "agents"
            name: "agents/"
            scope: "shared"
            note: "公共基础 Agent/workagent 与应用专属 Agent 的合并目录"
            physical: "开发 AI Git + 测试公共 AI Git + 测试 AI Git"
            children:
              -
                id: "requirements-agent"
                name: "01_需求智能体/"
                scope: "development"
                note: "需求 Agent"
                role: "agent"
                physical: "开发 AI Git"
              -
                id: "design-agent"
                name: "02_设计智能体/"
                scope: "development"
                note: "设计 Agent"
                role: "agent"
                physical: "开发 AI Git"
                children:
                  -
                    id: "outline-design"
                    name: "01_概要设计/"
                    scope: "development"
                    note: "概要设计 Agent"
                    role: "agent"
                    physical: "开发 AI Git"
                    children:
                      -
                        id: "outline-rules"
                        name: "rules/"
                        scope: "shared"
                        note: "公共规约与应用规约"
                        physical: "开发 AI Git"
                        children:
                          -
                            id: "outline-common-rule"
                            name: "概要设计公共规约.md"
                            scope: "development"
                            note: "开发公共规约"
                            physical: "开发 AI Git"
                          -
                            id: "outline-app-rule"
                            name: "概要设计应用规约.md"
                            scope: "development"
                            note: "开发应用规约"
                            physical: "开发 AI Git"
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
                    physical: "开发 AI Git"
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
                            physical: "开发 AI Git"
                          -
                            id: "detail-app-rule"
                            name: "详细设计应用规约.md"
                            scope: "development"
                            note: "开发应用规约"
                            physical: "开发 AI Git"
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
                    physical: "开发 AI Git"
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
                            physical: "开发 AI Git"
                          -
                            id: "program-app-rule"
                            name: "程序设计应用规约.md"
                            scope: "development"
                            note: "开发应用规约"
                            physical: "开发 AI Git"
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
                physical: "开发 AI Git"
              -
                id: "testing-agent"
                name: "04_测试智能体/"
                scope: "testing"
                note: "测试 Agent 与 workagent 分类"
                physical: "测试公共 AI Git + 测试 AI Git"
                children:
                  -
                    id: "test-design-agent"
                    name: "01_测试设计/"
                    scope: "testing"
                    note: "用户可 @ 的测试设计入口"
                    role: "agent"
                    physical: "测试公共 AI Git + 测试 AI Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "test-design-agent-file"
                        name: "test-design-orchestrator.md"
                        scope: "testing"
                        note: "Test Design（测试设计）Agent 定义"
                        physical: "测试公共 AI Git"
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
                            physical: "测试公共 AI Git"
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
                            id: "test-design-app-rule"
                            name: "测试设计应用规约.md"
                            scope: "testing"
                            note: "应用规约"
                            physical: "测试 AI Git"
                      -
                        id: "test-analysis-workagent"
                        name: "001 Test Analysis（测试分析）/"
                        scope: "testing"
                        note: "供测试设计 Agent 调用"
                        role: "workagent"
                        physical: "测试公共 AI Git"
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
                        physical: "测试公共 AI Git"
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
                        physical: "测试公共 AI Git"
                        implementation: "implemented"
                        children:
                          -
                            id: "test-review-workagent-file"
                            name: "test-design-review.md"
                            scope: "testing"
                  -
                    id: "test-execution-agent"
                    name: "02_测试执行/"
                    scope: "testing"
                    note: "用户可 @ 的测试执行入口"
                    role: "agent"
                    physical: "测试公共 AI Git + 测试 AI Git"
                    implementation: "implemented"
                    children:
                      -
                        id: "test-execution-agent-file"
                        name: "test-execution-agent.md"
                        scope: "testing"
                        note: "Test Execution（测试执行）Agent 定义"
                        physical: "测试公共 AI Git"
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
                            physical: "测试公共 AI Git"
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
                            physical: "测试 AI Git"
                      -
                        id: "api-execution-workagent"
                        name: "001 API Test Execution（接口测试执行）/"
                        scope: "testing"
                        note: "供测试执行 Agent 调用"
                        role: "workagent"
                        physical: "测试公共 AI Git"
                        implementation: "implemented"
                        children:
                          -
                            id: "api-execution-workagent-file"
                            name: "test-execution-api.md"
                            scope: "testing"
                  -
                    id: "application-test-agent"
                    name: "<应用专属测试 Agent>/"
                    scope: "testing"
                    note: "应用专属用户入口或上层编排占位"
                    role: "agent"
                    physical: "测试 AI Git"
                    implementation: "planned"
                  -
                    id: "application-test-workagent"
                    name: "<应用专属测试 workagent>/"
                    scope: "testing"
                    note: "应用专属隐藏工作单元占位"
                    role: "workagent"
                    physical: "测试 AI Git"
                    implementation: "planned"
          -
            id: "mcp"
            name: "mcp/"
            scope: "shared"
            note: "开发与测试运行配置"
            physical: "开发 AI Git + 测试公共 AI Git + 测试 AI Git"
          -
            id: "engineering-rules"
            name: "rules/"
            scope: "shared"
            note: "开发规约、测试公共规约与应用测试规约"
            physical: "开发 AI Git + 测试公共 AI Git + 测试 AI Git"
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
            physical: "开发 AI Git + 测试公共 AI Git + 测试 AI Git"
            children:
              -
                id: "coding-skills"
                name: "coding/"
                scope: "development"
                note: "开发 Skill"
                physical: "开发 AI Git"
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
                id: "test-design-skill"
                name: "test-design/"
                scope: "testing"
                note: "Test Design（测试设计）"
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试公共 AI Git"
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
                physical: "测试 AI Git"
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
        physical: "开发 AI Git + 测试 AI Git"
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
        physical: "测试 AI Git · 仅本地提交"
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
        physical: "开发 AI Git + 测试 AI Git"
        children:
          -
            id: "application-architecture"
            name: "应用架构.md"
            scope: "development"
            note: "应用关系、服务节点和功能模块清单"
            physical: "开发 AI Git"
          -
            id: "technical-architecture"
            name: "技术架构/"
            scope: "shared"
            note: "开发工程概览与测试场景资产共用目录"
            physical: "开发 AI Git + 测试 AI Git"
            children:
              -
                id: "engineering-overview-a"
                name: "工程概览_A.md"
                scope: "development"
                note: "技术栈、接口清单和应用关系"
                physical: "开发 AI Git"
              -
                id: "engineering-overview-b"
                name: "工程概览_B.md"
                scope: "development"
                note: "技术栈、接口清单和应用关系"
                physical: "开发 AI Git"
              -
                id: "engineering-overview-more"
                name: "..."
                scope: "development"
                note: "其他工程概览"
                physical: "开发 AI Git"
              -
                id: "test-overview"
                name: "测试概述.md"
                scope: "testing"
                note: "目录索引、非功能要求、公共案例和环境"
                physical: "测试 AI Git"
              -
                id: "scenario-x"
                name: "场景测试说明书_XXX.md"
                scope: "testing"
                note: "场景图、业务规则、核心要素和测试关注点"
                physical: "测试 AI Git"
              -
                id: "scenario-y"
                name: "场景测试说明书_YYY.md"
                scope: "testing"
                physical: "测试 AI Git"
              -
                id: "scenario-more"
                name: "..."
                scope: "testing"
                note: "其他场景测试说明书"
                physical: "测试 AI Git"
          -
            id: "functional-module"
            name: "功能模块/"
            scope: "shared"
            note: "开发功能说明与测试设计案例共用目录"
            physical: "开发 AI Git + 测试 AI Git"
            children:
              -
                id: "functional-module-x"
                name: "功能模块_XXX.md"
                scope: "development"
                note: "业务说明书"
                physical: "开发 AI Git"
              -
                id: "functional-module-y"
                name: "功能模块_YYY.md"
                scope: "development"
                note: "业务说明书"
                physical: "开发 AI Git"
              -
                id: "functional-design-x1"
                name: "测试设计文档_X1.md"
                scope: "testing"
                note: "等价类表、路径图等测试设计"
                physical: "测试 AI Git"
              -
                id: "functional-design-x2"
                name: "测试设计文档_X2.md"
                scope: "testing"
                physical: "测试 AI Git"
              -
                id: "functional-case-x1"
                name: "测试案例_X1.md"
                scope: "testing"
                physical: "测试 AI Git"
              -
                id: "functional-case-x2"
                name: "测试案例_X2.md"
                scope: "testing"
                physical: "测试 AI Git"
              -
                id: "functional-more"
                name: "..."
                scope: "testing"
                note: "其他功能测试资产"
                physical: "测试 AI Git"
          -
            id: "functional-docs"
            name: "功能文档/"
            scope: "development"
            note: "开发功能文档"
            physical: "开发 AI Git"
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
            physical: "开发 AI Git + 测试 AI Git"
            children:
              -
                id: "gauss-schema"
                name: "F-ABC_Gauss_1.yaml"
                scope: "development"
                note: "开发数据架构资产"
                physical: "开发 AI Git"
              -
                id: "mysql-schema"
                name: "F-ABC_MySQL_1.yaml"
                scope: "development"
                note: "开发数据架构资产"
                physical: "开发 AI Git"
              -
                id: "data-entity-x1"
                name: "数据实体_X1.md"
                scope: "testing"
                physical: "测试 AI Git"
              -
                id: "data-entity-x2"
                name: "数据实体_X2.md"
                scope: "testing"
                physical: "测试 AI Git"
          -
            id: "business-knowledge"
            name: "业务知识/"
            scope: "development"
            note: "开发业务知识资产"
            physical: "开发 AI Git"
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
            physical: "测试 AI Git"
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
        physical: "开发业务代码 Git"
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
        physical: "开发业务代码 Git"
  agentDesignSummary: "workagent 可以独立完成任务，但主要供上层 Agent 编排调用；OpenCode 技术实现为 mode: subagent、hidden: true，不建议用户单独调用。"
  agentDesignRows:
    -
      scope: "需求 / 设计 / 编码"
      role: "开发 Agent"
      description: "全部放在开发 AI Git；开发规约、模板和评估资源也不拆到测试 Git。"
    -
      scope: "测试设计 / 测试执行"
      role: "测试 Agent"
      description: "跨应用基础放测试公共 AI Git，应用专属编排和扩展放测试 AI Git。"
    -
      scope: "测试分析 / 案例生成 / 案例审核 / 接口执行"
      role: "workagent"
      description: "跨应用工作单元放测试公共 AI Git；技术上是 hidden subagent，只由上层测试 Agent 调用。"
    -
      scope: "测试方法与应用知识"
      role: "Skill"
      description: "通用测试方法放测试公共 AI Git，应用专属 Skill 放测试 AI Git；开发 Skill 全部放开发 AI Git。"
  ownershipRows:
    -
      directory: "测试公共 Agent / Skills"
      content: "跨应用通用测试智能体、workagent 和测试方法"
      builders: "效能组、测试管理组"
      publisher: "平台超级管理员"
    -
      directory: "测试公共规约"
      content: "跨应用统一质量要求和公共测试规约"
      builders: "测试管理组"
      publisher: "平台超级管理员"
    -
      directory: "测试 Agent / Skills"
      content: "应用测试设计、执行、分析智能体及方法"
      builders: "测试组"
      publisher: "应用管理员及以上"
    -
      directory: "测试应用规约"
      content: "结合应用特点补充的测试设计与执行规约"
      builders: "测试组"
      publisher: "应用管理员及以上"
    -
      directory: "开发 Agent / Skills / 规约"
      content: "需求、设计、编码智能体及全部开发配置"
      builders: "研发团队"
      publisher: "开发 AI Git 负责人"
    -
      directory: "docs/**"
      content: "开发已有资产与新增测试资产的应用级稳定沉淀"
      builders: "测试组"
      publisher: "应用管理员审核发布"
    -
      directory: "spec/<需求项>"
      content: "具体研发阶段的输入输出产物（需求、设计、编码、测试）"
      builders: "当前用户与 Agent"
      publisher: "仅本地提交，禁止推送"
    -
      directory: "archive/<年月>/<需求项>"
      content: "开发与测试各阶段完成评审后的归档快照"
      builders: "研发团队、测试组"
      publisher: "应用管理员受控发布"
    -
      directory: "git-repo-A · git-repo-B"
      content: "业务代码、单测和工程文档"
      builders: "开发团队"
      publisher: "开发业务代码 Git 负责人"
  responsibilityRules:
    -
      index: "01"
      title: "测试公共能力共同建设"
      description: "测试公共 Agent / Skills 由效能组和测试管理组建设。"
    -
      index: "02"
      title: "规约分层负责"
      description: "测试管理组维护测试公共规约，测试组维护测试应用规约。"
    -
      index: "03"
      title: "应用资产归测试组"
      description: "docs/** 内容由测试组统一建设并受控发布。"
---

# 开发与测试目录设计

本页以标准工程目录文件为准，将开发已有目录与测试扩展合并为一棵可逐级展开的应用（服务群组）工程树。目录名统一使用中性色，小标签分别说明开发/测试范围、Agent/workagent 形态和物理 Git；工程根是多个 worktree 的组合视图，不是一个新的 Git 仓库。

> 后续调整目录、Agent/workagent/Skill 名称、Git 归属或建设职责时，只修改本文件顶部的 `directoryMapping` 数据；`DirectoryMapping.vue` 仅负责通用展示和展开交互。

<DirectoryMapping />

## 目录设计原则

### 保留一个完整工程视图

研发和测试不是两棵互不相干的目录。测试目录沿用应用工程中的需求、设计、编码、业务 Git 和知识文档，再扩展测试智能体、四阶段 `spec`、测试资产及归档能力。

### `ai-agent` 定义怎样工作

`ai-agent` 是面向建设者的逻辑分类，不应原样复制到 OpenCode 原生目录。目标物理层固定为四类 Git：开发 AI Git、测试公共 AI Git、测试 AI Git、开发业务代码 Git。当前 Config 区域的公共配置根 `opencode/{agents,skills}` 对应测试公共 AI Git，工作空间配置根 `.opencode/{agents,skills}` 对应测试 AI Git；开发 AI Git 和开发业务代码 Git 作为另外两个 worktree 来源组合进页面工程树。

`agents/` 在页面上是一棵逻辑合并树，物理上没有新的 `agents` 仓库：开发 Agent 来自开发 AI Git，跨应用测试 Agent/workagent 来自测试公共 AI Git，应用测试 Agent/workagent 来自测试 AI Git。树中每行按三个互不混用的维度表达：

- 范围标签：需求、设计、编码为开发，测试智能体为测试，`agents/` 表示同时包含开发与测试。
- 形态标签：用户入口和上层编排标为 `Agent`；对象分析、生成、审核、接口执行等独立工作单元标为 `workagent`。
- 物理 Git：需求、设计、编码及其开发规约全部放开发 AI Git；测试公共规约放测试公共 AI Git；应用测试规约放测试 AI Git。

- 当前测试公共 Config 中只有 Test Design（测试设计）和 Test Execution（测试执行）是用户可选择或 `@` 的 `Agent`，使用 `mode: all`；界面以英文名为主、中文名为辅，运行时仍使用原技术 ID，目录页标记为“已实现”。
- 当前已确定的 `test-design-analysis.md`、`test-design-generation.md`、`test-design-review.md`、`test-execution-api.md` 都能独立完成一件工作，统一称为 `workagent`，放在测试公共 AI Git 的 `opencode/agents/*.md`，目录页同样标记为“已实现”。
- `workagent` 的 OpenCode 技术实现统一使用 `mode: subagent`、`hidden: true`，不进入用户的 `@` 补全，主要由测试设计或测试执行 Agent 编排调用；不建议用户绕过上层流程单独调用。
- 应用专属测试 Agent/workagent 放在测试 AI Git 的 `.opencode/agents/*.md`，是否允许用户直接 `@` 由应用定义决定；尚无具体定义时以“未实现”灰显占位。
- 测试 Agent 内的公共规约和应用规约都属于测试范围，统一使用“测试”标签；测试公共 AI Git 与测试 AI Git 的差异只由最右侧物理 Git 标签表达。
- 开发 Skill 全部放开发 AI Git；当前 12 个 `test-design*`、接口执行、自动化文档、测试报文和格式校验公共 Skill 放测试公共 AI Git，应用测试知识和专属方法放测试 AI Git。
- `rules`、`template`、`eval` 放进对应 Skill 资源目录，或由 `AGENTS.md` / `opencode.jsonc` 的 `instructions` 引用，不能作为普通 `.md` 混放在 `.opencode/agents/**`。

OpenCode 会递归读取 `agents/**/*.md`，文件相对路径会成为 Agent 名称；例如 `.opencode/agents/test/review.md` 对应 `@test/review`。`mode: subagent` 和 `mode: all` 可进入 `@` 候选，`hidden: true` 会隐藏候选。`workagent` 仍是 Agent，只是在 OpenCode 中以隐藏的 subagent 方式供上层 Agent 调用。

### `spec` 保存研发阶段输入输出产物

`spec/<需求项>/` 保存具体研发阶段的输入输出产物，固定分为 `01-需求`、`02-设计`、`03-编码`、`04-测试`。测试阶段再分测试设计和测试执行，并同时支持流程测试与需求子条目。`spec` 使用测试 AI Git 派生的个人本地 worktree，只允许本地提交，不直接推送远端。

### `docs` 保存可复用测试资产

`docs` 是页面合并目录，不是单一物理仓库：开发资产放开发 AI Git，测试资产放测试 AI Git。它保留文件给出的真实技术目录：应用架构、技术架构、功能模块、数据架构和部署架构；页面可按业务、功能、架构三个视角组织，但不改变磁盘目录名。

`docs/`、`技术架构/`、`功能模块/`、`数据架构/` 使用“开发 + 测试”范围标签；其中工程概览、功能说明和数据库 YAML 等文件标为开发，测试概述、场景说明书、测试设计、测试案例和数据实体标为测试。`功能文档/`、`业务知识/` 当前只有开发资产，`部署架构/` 当前只有测试资产。

- 开发已有资产：工程概览、功能模块业务说明、功能文档、数据库 YAML 和业务知识。
- 测试新增资产：测试概述、场景测试说明书、测试设计文档、测试案例、数据实体和部署架构。
- 业务视角：场景测试说明书中的业务规则、场景图、核心要素和测试关注点。
- 功能视角：功能模块下的测试设计文档、测试案例和功能验证基线。
- 架构视角：应用架构、测试概述、数据实体、部署结构和非功能要求。

### `archive` 保存开发测试共用归档

需求、设计、编码和测试各阶段完成评审的产物，由受控流程复制到各自 AI Git 的 `archive/<年月>/<需求项>/`，页面合并显示为开发、测试共用的共享快照。它不是把本地 `spec` 分支直接推送远端，也不替代开发业务代码 Git 的正式交付。

## Git 与发布边界

1. 开发 AI Git 保存全部开发 Agent / Skills / 规约 / docs 开发资产，由研发体系按开发发布规则维护。
2. 测试公共 AI Git 保存跨应用测试 Agent / workagent / Skills / 公共规约，由效能组、测试管理组建设，平台超级管理员发布。
3. 测试 AI Git 保存应用测试 Agent / Skills / 应用规约、docs 测试资产和归档；`spec` 使用其派生的本地个人分支，只允许本地提交，不设置远端跟踪。
4. `git-repo-A`、`git-repo-B` 等开发业务代码 Git 由开发团队建设，测试工作区只组合对应个人 worktree，不复制整库到 `spec` 或 `docs`。
5. 页面展示可以合并多个来源，但每个目录仍保留自己的 Git、分支、权限和发布规则。
