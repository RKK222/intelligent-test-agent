import DefaultTheme from "vitepress/theme";
import DirectoryMapping from "./components/DirectoryMapping.vue";
import "./custom.css";

export default {
  extends: DefaultTheme,
  enhanceApp({ app }) {
    app.component("DirectoryMapping", DirectoryMapping);
  }
};
