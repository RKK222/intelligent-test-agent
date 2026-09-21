# 路径图

```mermaid
flowchart TD
  A["{{startNode}}"] --> B{"{{decisionNode}}"}
  B -->|{{condition1}}| C["{{node1}}"]
  B -->|{{condition2}}| D["{{node2}}"]
  C --> E["{{terminalState1}}"]
  D --> F["{{terminalState2}}"]
```
