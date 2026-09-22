import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import {
  MantineProvider,
  PasswordInput,
  TextInput,
  Textarea,
} from "@mantine/core";
import "./index.css";
import "@mantine/core/styles.css";
import "@mantine/notifications/styles.css";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Notifications } from "@mantine/notifications";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ModalsProvider } from "@mantine/modals";
import {
  browserTracingIntegration,
  init,
  replayIntegration,
} from "@sentry/react";

init({
  dsn: "https://938dcbf09e4bc4e05a089d6c36f830da@us.sentry.io/4506695788527616",
  integrations: [browserTracingIntegration(), replayIntegration()],
  tracesSampleRate: 1.0,
  tracePropagationTargets: ["localhost", /^https:\/\/hackmerlin\.io\/api/],
  replaysSessionSampleRate: 0.1,
  replaysOnErrorSampleRate: 1.0,
});

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 2 } },
});

// Fix 6: ING Orange theme — primary color matches brand across all Mantine components
const ingTheme = {
  primaryColor: "orange",
  colors: {
    orange: [
      "#fff4e6",
      "#ffe8cc",
      "#ffd09b",
      "#ffb766",
      "#ff9f3a",
      "#ff8c00",
      "#ff6600",
      "#e55a00",
      "#cc5000",
      "#b34500",
    ] as [string, string, string, string, string, string, string, string, string, string],
  },
  /**
   * Every text field defaults to 16px. Mantine's default "sm" renders at 14px, and iOS Safari
   * zooms the entire viewport when focusing any input below 16px - which on the game screen
   * left players scrolled sideways with the Ask button off screen. Most players are on phones.
   */
  components: {
    TextInput: TextInput.extend({ defaultProps: { size: "md" } }),
    PasswordInput: PasswordInput.extend({ defaultProps: { size: "md" } }),
    Textarea: Textarea.extend({ defaultProps: { size: "md" } }),
  },
};

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <MantineProvider theme={ingTheme}>
      <ModalsProvider>
        <QueryClientProvider client={queryClient}>
          <App />
          <ReactQueryDevtools initialIsOpen={false} />
        </QueryClientProvider>
      </ModalsProvider>
      <Notifications position="top-center" />
    </MantineProvider>
  </React.StrictMode>,
);
