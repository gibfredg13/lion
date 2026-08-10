import { PropsWithChildren } from "react";
import {
  Anchor,
  Center,
  Container,
  FocusTrap,
  Group,
  Text,
  Paper,
  Modal,
  List,
} from "@mantine/core";
import { useQuery } from "@tanstack/react-query";
import { useToggle } from "@mantine/hooks";
import { useLion } from "../hooks/lion.ts";

export default function LionLayout({ children }: PropsWithChildren) {
  const [leaderboardOpen, toggleLeaderboard] = useToggle();
  return (
    <div style={{ display: "grid", placeItems: "center", height: "100%" }}>
      <Container size="xs">
        <Center style={{ fontSize: "120px", marginBottom: "20px" }}>🦁</Center>
        <FocusTrap active>
          <Paper withBorder shadow="md" p="sm" radius="sm">
            {children}
          </Paper>
        </FocusTrap>
        <Center>
          <Anchor
            type="button"
            fz="sm"
            m="sm"
            c="dimmed"
            onClick={() => toggleLeaderboard()}
          >
            Brave Hunters
          </Anchor>
          <Modal
            opened={leaderboardOpen}
            onClose={toggleLeaderboard}
            title="Warriors who conquered the Lion"
            centered
          >
            <Leaderboard />
            <Text fz="sm" m="sm" c="dimmed">
              Updated continuously
            </Text>
          </Modal>
        </Center>
      </Container>
    </div>
  );
}

function Leaderboard() {
  const { getLeaderboard } = useLion();
  const leaderboard = useQuery({
    queryKey: ["leaderboard"],
    queryFn: getLeaderboard,
  });
  if (leaderboard.isLoading) return <Text>Loading...</Text>;
  if (leaderboard.isError || !leaderboard.data) return <Text>Error!</Text>;
  return (
    <List>
      {leaderboard.data
        .filter((it: any) => it.name)
        .map((entry: any) => (
          <List.Item key={entry.id}>
            <Group justify="space-between">
              <Text>
                {entry.name} (
                {new Date(
                  Date.parse(entry.finishedAt) - Date.parse(entry.startedAt),
                )
                  .toISOString()
                  .slice(11, -5)}
                )
              </Text>
            </Group>
          </List.Item>
        ))}
    </List>
  );
}
