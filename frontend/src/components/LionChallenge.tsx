import { useForm } from "@mantine/form";
import { Button, Progress, Textarea, Title } from "@mantine/core";
import { getHotkeyHandler, useMediaQuery } from "@mantine/hooks";

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

  /**
   * On a soft keyboard Enter is the newline key, so submitting on it makes the box impossible to
   * write more than one line in. Phones keep mod+Enter and the full-width Ask button.
   */
  const isDesktop = useMediaQuery("(min-width: 48em)", false, {
    // Read matchMedia on the first render instead of in an effect. There is no SSR here, and
    // deferring it flashes the phone layout for a frame on every desktop load.
    getInitialValueInEffect: false,
  });

  return (
    <form onSubmit={handleSubmit}>
      <Title size="h4">Trial {level}</Title>
      <Progress mt="xs" value={(level / (maxLevel + 1)) * 100} size="xs" color="orange" />
      <Textarea
        data-autofocus
        mt="sm"
        placeholder="Speak with the lion here..."
        withAsterisk
        maxLength={150}
        autosize
        minRows={2}
        maxRows={5}
        onKeyDown={getHotkeyHandler(
          isDesktop
            ? [
                ["mod+Enter", () => handleSubmit()],
                ["Enter", () => handleSubmit()],
              ]
            : [["mod+Enter", () => handleSubmit()]],
        )}
        {...form.getInputProps("prompt")}
      />
      <Button disabled={disabled} type="submit" fullWidth mt="sm" color="orange">
        Ask
      </Button>
    </form>
  );
}
