import React from "react";
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
import LoginPage from "./components/LoginPage.tsx";
import RegisterPage from "./components/RegisterPage.tsx";
import ProtectedRoute from "./components/ProtectedRoute.tsx";
import LeaderboardTV from "./components/tv/LeaderboardTV";
import Navigation from "./components/Navigation.tsx";
import { BrowserRouter as Router, Routes, Route, Navigate } from "react-router-dom";

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
              <AdminRoute>
                <AdminApp />
              </AdminRoute>
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
        <Route path="/leaderboard/tv" element={<LeaderboardTV />} />
      </Routes>
    </Router>
  );
}

/** Fix 6: Shared layout with Navigation bar for all authenticated pages */
function AuthenticatedLayout({ children }: { children: React.ReactNode }) {
  return (
    <>
      <Navigation />
      {children}
    </>
  );
}

/** Fix 7: AdminRoute guard — redirects non-admins to the challenge page */
function AdminRoute({ children }: { children: React.ReactNode }) {
  const session = useSession();
  if (session.isLoading) return <QuestLoader />;
  if (!session.data?.isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function MainApp() {
  const session = useSession();
  if (session.isLoading || !session.data) return <QuestLoader />;
  return (
    <AuthenticatedLayout>
      <LionLayout>
        <Level
          currentLevel={session.data.currentLevel}
          maxLevel={session.data.maxLevel}
        />
      </LionLayout>
    </AuthenticatedLayout>
  );
}

function AdminApp() {
  return (
    <AuthenticatedLayout>
      <AdminDashboard />
    </AuthenticatedLayout>
  );
}

function LeaderboardApp() {
  return (
    <AuthenticatedLayout>
      <Leaderboard />
    </AuthenticatedLayout>
  );
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
        key={currentLevel}
        disabled={merlin.question.isPending}
        onSubmit={(prompt, reset) => {
          merlin.question.mutate(prompt, {
            onSuccess: (result: string) => {
              setResponse(result);
            },
          });
          // Emptied as soon as the question is sent, not on reply: LionSpeak is already showing
          // its skeleton, so leaving the text in place for the whole round-trip reads as a
          // failed submit.
          reset();
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
                    <Title order={2} component="span" c="green.6" fw={900}>
                      🎉 Victory!
                    </Title>
                  ),
                  children: (
                    <>
                      <Text>{result.finishedMessage}</Text>
                      <Button
                        fullWidth
                        color="green"
                        onClick={() => modals.closeAll()}
                        mt="md"
                      >
                        Continue
                      </Button>
                    </>
                  ),
                });
              }
              // Outside the branch above: on the final level no modal opens, and the previous
              // level's reply used to linger behind the Victory screen.
              setResponse(undefined);

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
