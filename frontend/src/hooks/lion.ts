import { useMutation } from "@tanstack/react-query";
import wretch from "wretch";
import { MerlinSession } from "./session.ts";
import { notifications } from "@mantine/notifications";

interface ApiError {
  title: string;
  message?: string;
}

const api = wretch()
  .options({ credentials: "include" })
  .customError<ApiError>(async (error: any, response: any) => {
  const json = await response.json();
  return {
    title: json.error || error.response.statusText,
    message: json.message,
  };
});

export function useLion() {
  return {
    question: useMutation({
      mutationFn: (prompt: string) =>
        api
          .url("/api/question")
          .headers({ "Content-Type": "text/plain" })
          .post(prompt)
          .text(),
      onError: (error: ApiError) => {
        notifications.show({
          title: error.title,
          message: error.message,
          color: "red",
        });
      },
    }),
    submit: useMutation({
      mutationFn: (password: string) =>
        api
          .url("/api/submit")
          .headers({ "Content-Type": "text/plain" })
          .post(password)
          .json<MerlinSession>(),
      onError: () => {
        notifications.show({
          title: "The lion didn't reveal it",
          message: "That's not the word the lion is protecting.",
          color: "red",
        });
      },
    }),
    reset: useMutation({
      mutationFn: () => api.url("/api/reset").post().res(),
      onSuccess: () => {
        notifications.show({
          title: "Your journey restarts.",
          message: "Return to face the lion again!",
          color: "orange",
        });
      },
    }),
    addName: useMutation({
      mutationFn: ({ id, name }: { id?: string; name: string }) => {
        return api.url("/api/leaderboard/submit").post({ id, name }).res();
      },
      onSuccess: () => {
        notifications.show({
          title: "You're on the board!",
          message: "Your name joins the greatest hunters!",
          color: "orange",
        });
      },
      onError: () => {
        notifications.show({
          title: "The lion didn't reveal it",
          message: "That's not the word the lion is protecting.",
          color: "red",
        });
      },
    }),
    getLeaderboard: () => {
      const data: Promise<LeaderboardEntry[]> = api
        .url("/api/leaderboard")
        .get()
        .json();
      return data;
    },
  };
}

interface LeaderboardEntry {
  id: string;
  name: string;
  startedAt: string;
  finishedAt: string;
}
