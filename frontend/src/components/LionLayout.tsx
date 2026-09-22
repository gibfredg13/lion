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
import { useMediaQuery, useToggle } from "@mantine/hooks";
import { useLion } from "../hooks/lion.ts";

export default function LionLayout({ children }: PropsWithChildren) {
  const [leaderboardOpen, toggleLeaderboard] = useToggle();
  /**
   * Most players are on phones. Vertical centring plus an open soft keyboard pushes the input
   * off screen, and the focus trap pops that keyboard on every level change, so both are
   * desktop-only affordances here.
   */
  const isDesktop = useMediaQuery("(min-width: 48em)", false, {
    // Read matchMedia on the first render instead of in an effect. There is no SSR here, and
    // deferring it flashes the phone layout for a frame on every desktop load.
    getInitialValueInEffect: false,
  });
  return (
    <div
      style={{
        display: "grid",
        justifyItems: "center",
        alignItems: isDesktop ? "center" : "start",
        minHeight: "100dvh",
        padding: "1rem 0",
      }}
    >
      <Container size="xs" px="sm" w="100%">
        <Center fz={{ base: 56, sm: 120 }} mb={{ base: 8, sm: 20 }}>
          🦁
        </Center>
        <FocusTrap active={isDesktop}>
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
