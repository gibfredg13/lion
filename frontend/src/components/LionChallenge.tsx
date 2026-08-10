import { useForm } from "@mantine/form";
import { Button, Progress, Textarea, Title } from "@mantine/core";
import { getHotkeyHandler } from "@mantine/hooks";

interface LionChallengeProps {
  level: number;
  maxLevel: number;
  onSubmit: (prompt: string, reset: () => void) => void;
  disabled?: boolean;
}

export default function LionChallenge({
  level,
  maxLevel,
  onSubmit,
  disabled,
}: LionChallengeProps) {
  const form = useForm({
    initialValues: {
      prompt: "",
    },
    validate: {
      prompt: (value) =>
        value.length < 2 ? "Prompt must have at least 2 letters" : null,
    },
  });

  const handleSubmit = form.onSubmit((values) =>
    onSubmit(values.prompt, form.reset),
  );

  return (
    <form onSubmit={handleSubmit}>
      <Title size="h4">Trial {level}</Title>
      <Progress mt="xs" value={(level / (maxLevel + 1)) * 100} size="xs" />
      <Textarea
        data-autofocus
        mt="sm"
        placeholder="Speak with the lion here..."
        withAsterisk
        maxLength={150}
        minRows={4}
        onKeyDown={getHotkeyHandler([
          ["mod+Enter", () => handleSubmit()],
          ["Enter", () => handleSubmit()],
        ])}
        {...form.getInputProps("prompt")}
      />
      <Button disabled={disabled} type="submit" fullWidth mt="sm">
        Ask
      </Button>
    </form>
  );
}
