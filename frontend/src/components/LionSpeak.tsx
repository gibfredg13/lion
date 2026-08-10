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
      style={{ height: 130, overflow: "auto" }}
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
