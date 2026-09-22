import { Blockquote, Skeleton, Stack, Text } from "@mantine/core";

export default function LionSpeak({
  isLoading,
  response,
}: {
  isLoading: boolean;
  response: string;
}) {
  return (
    <Blockquote
      // A fixed height wasted space on a short reply and trapped a long one in a tiny
      // scroller on a phone. Grow with the answer, capped against the viewport.
      style={{ minHeight: 96, maxHeight: "32dvh", overflow: "auto" }}
      cite="– The Great Lion"
      p="sm"
    >
      {isLoading ? (
        <Stack gap={1}>
          <Skeleton height={8} mt={6} radius="xl" />
          <Skeleton height={8} mt={6} radius="xl" />
          <Skeleton height={8} mt={6} radius="xl" />
          <Skeleton height={8} mt={6} radius="xl" />
        </Stack>
      ) : (
        <Text size="sm">{response}</Text>
      )}
    </Blockquote>
  );
}
