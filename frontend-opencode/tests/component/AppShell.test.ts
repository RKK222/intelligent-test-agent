import { render, screen } from "@testing-library/vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import App from "@/App.vue";
import { routes } from "@/router";

describe("App shell", () => {
  it("renders the opencode-style shell without marketing content", async () => {
    const router = createRouter({ history: createWebHistory(), routes });
    router.push("/");
    await router.isReady();

    render(App, {
      global: {
        plugins: [createPinia(), router]
      }
    });

    expect(screen.getByRole("banner")).toHaveTextContent("opencode");
    expect(screen.getByRole("button", { name: /new session/i })).toBeInTheDocument();
    expect(screen.queryByText(/landing/i)).not.toBeInTheDocument();
  });
});
