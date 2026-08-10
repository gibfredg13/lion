import { Button, Stack, Text, Title } from "@mantine/core";
import { useState } from "react";
import { useLion } from "./hooks/lion.ts";
import LionLayout from "./components/LionLayout.tsx";
import LionSpeak from "./components/LionSpeak.tsx";
import LionChallenge from "./components/LionChallenge.tsx";
import { SecretWordForm } from "./components/SecretWordForm.tsx";
import { MerlinSession, useSession } from "./hooks/session.ts";
import { useQueryClient } from "@tanstack/react-query";
import QuestLoader from "./components/QuestLoader.tsx";
import Victory from "./components/Victory.tsx";
import { modals } from "@mantine/modals";
import AdminDashboard from "./components/AdminDashboard.tsx";
import Leaderboard from "./components/Leaderboard.tsx";
import RealtimeBreachDashboard from "./components/RealtimeBreachDashboard.tsx";
import LoginPage from "./components/LoginPage.tsx";
import RegisterPage from "./components/RegisterPage.tsx";
import ProtectedRoute from "./components/ProtectedRoute.tsx";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <MainApp />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <ProtectedRoute>
              <AdminApp />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin/breaches"
          element={
            <ProtectedRoute>
              <BreachesApp />
            </ProtectedRoute>
          }
        />
        <Route
          path="/leaderboard"
          element={
            <ProtectedRoute>
              <LeaderboardApp />
            </ProtectedRoute>
          }
        />
      </Routes>
    </Router>
  );
}

function MainApp() {
  const session = useSession();
  if (session.isLoading || !session.data) return <QuestLoader />;
  return (
    <LionLayout>
      <Level
        currentLevel={session.data.currentLevel}
        maxLevel={session.data.maxLevel}
      />
    </LionLayout>
  );
}

function AdminApp() {
  return <AdminDashboard />;
}

function BreachesApp() {
  return <RealtimeBreachDashboard />;
}

function LeaderboardApp() {
  return <Leaderboard />;
}

function Level({
  currentLevel,
  maxLevel,
}: {
  currentLevel: number;
  maxLevel: number;
}) {
  const queryClient = useQueryClient();
  const merlin = useLion();
  const session = useSession();
  const [response, setResponse] = useState<string>();

  if (currentLevel > maxLevel)
    return (
      <Victory
        id={session.data?.id}
        submittedName={session.data?.submittedName}
        onReset={() => {
          merlin.reset.mutate(undefined, {
            onSuccess: async () => {
              await queryClient.refetchQueries({ queryKey: ["session"] });
            },
          });
        }}
      />
    );

  return (
    <Stack gap="xs">
      <Title size="h4">The Lion's Challenge</Title>
      <Text size="xs">
        Face the mighty lion through seven trials. Ask clever questions to uncover each
        level's secret word. The lion grows stronger with each level. Can you complete all seven trials?
      </Text>
      <LionChallenge
        disabled={merlin.question.isPending}
        onSubmit={(prompt) => {
          merlin.question.mutate(prompt, {
            onSuccess: (result: string) => {
              setResponse(result);
            },
          });
        }}
        level={currentLevel}
        maxLevel={maxLevel}
      />
      <LionSpeak
        isLoading={merlin.question.isPending}
        response={response || "Hello traveler! Ask me anything..."}
      />
      <SecretWordForm
        disabled={merlin.submit.isPending}
        onSubmit={(password: string, reset: () => void) => {
          merlin.submit.mutate(password, {
            onSuccess: (result: MerlinSession) => {
              if (result.currentLevel < result.maxLevel) {
                modals.open({
                  centered: true,
                  title: (
                    <Title size="h3" component="span">
                      Victory!
                    </Title>
                  ),
                  children: (
                    <>
                      <Text>{result.finishedMessage}</Text>
                      <Button
                        fullWidth
                        onClick={() => modals.closeAll()}
                        mt="md"
                      >
                        Continue
                      </Button>
                    </>
                  ),
                });
                setResponse(undefined);
              }

              queryClient.setQueryData<MerlinSession>(["session"], (old) => {
                if (!old) return;
                return {
                  ...old,
                  currentLevel: result.currentLevel,
                };
              });
              reset();
            },
          });
        }}
      />
    </Stack>
  );
}
