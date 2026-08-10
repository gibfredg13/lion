import { useForm } from "@mantine/form";
import { Button, TextInput } from "@mantine/core";

interface SecretWordFormProps {
  disabled: boolean;
  onSubmit: (word: string, reset: () => void) => void;
}

export function SecretWordForm({
  disabled,
  onSubmit,
}: SecretWordFormProps) {
  const form = useForm({
    initialValues: {
      word: "",
    },
    validate: {
      word: (value) =>
        value.length < 2 ? "Word must have at least 2 letters" : null,
    },
  });
  return (
    <form
      onSubmit={form.onSubmit((values) =>
        onSubmit(values.word, form.reset),
      )}
    >
      <TextInput
        label="Enter the secret word"
        placeholder="SECRET WORD"
        styles={{ input: { textTransform: "uppercase" } }}
        {...form.getInputProps("word")}
      />
      <Button
        disabled={disabled}
        variant="light"
        color="green"
        type="submit"
        fullWidth
        mt="sm"
      >
        Submit
      </Button>
    </form>
  );
}
