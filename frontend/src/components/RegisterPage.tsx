import { Box, Button, Container, Group, PasswordInput, Stack, Text, TextInput, Title, Alert } from "@mantine/core";
import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";

const PASSWORD_REQUIREMENTS = [
  { re: /[a-z]/, label: "At least one lowercase letter" },
  { re: /[A-Z]/, label: "At least one uppercase letter" },
  { re: /\d/, label: "At least one number" },
  { re: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?]/, label: "At least one special character" },
  { re: /.{12,}/, label: "At least 12 characters" },
];

export default function RegisterPage() {
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const passwordStrength = PASSWORD_REQUIREMENTS.filter((req) => req.re.test(password));
  const isPasswordValid = passwordStrength.length === PASSWORD_REQUIREMENTS.length;
  const passwordsMatch = password === confirmPassword && password.length > 0;

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!isPasswordValid) {
      setError("Password does not meet all requirements");
      return;
    }

    if (!passwordsMatch) {
      setError("Passwords do not match");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ email, displayName, password }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || "Registration failed");
      }

      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      style={{
        minHeight: "100vh",
        background: "linear-gradient(135deg, #ff8c00 0%, #ff6b00 100%)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <Container size="xs" py="xl">
        <Stack gap="lg">
          <Box ta="center">
            <Title order={1} c="white" mb="xs">
              🦁 Lion's Quest
            </Title>
            <Text c="white" size="lg">
              Challenge Your Courage
            </Text>
          </Box>

          <Box
            p="xl"
            style={{
              backgroundColor: "white",
              borderRadius: "8px",
              boxShadow: "0 10px 40px rgba(0,0,0,0.2)",
            }}
          >
            <Stack gap="md">
              <Title order={2} size="h3" ta="center">
                Create Account
              </Title>

              {error && (
                <Alert color="red" title="Error">
                  {error}
                </Alert>
              )}

              <form onSubmit={handleRegister}>
                <Stack gap="md">
                  <TextInput
                    label="Email"
                    placeholder="your@email.com"
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.currentTarget.value)}
                    disabled={loading}
                  />

                  <TextInput
                    label="Display Name"
                    placeholder="Your Name"
                    required
                    value={displayName}
                    onChange={(e) => setDisplayName(e.currentTarget.value)}
                    disabled={loading}
                  />

                  <PasswordInput
                    label="Password"
                    placeholder="Create a strong password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.currentTarget.value)}
                    disabled={loading}
                  />

                  <Box>
                    <Text size="sm" fw={500} mb="xs">
                      Password Requirements:
                    </Text>
                    <Stack gap={4}>
                      {PASSWORD_REQUIREMENTS.map((req, index) => (
                        <Group key={index} gap="xs">
                          {passwordStrength.includes(req) ? (
                            <Text size="xl" c="#40c057" fw="bold">✓</Text>
                          ) : (
                            <Box
                              style={{
                                width: 16,
                                height: 16,
                                borderRadius: "50%",
                                border: "2px solid #ccc",
                              }}
                            />
                          )}
                          <Text
                            size="sm"
                            c={passwordStrength.includes(req) ? "#40c057" : "#666"}
                          >
                            {req.label}
                          </Text>
                        </Group>
                      ))}
                    </Stack>
                  </Box>

                  <PasswordInput
                    label="Confirm Password"
                    placeholder="Re-enter your password"
                    required
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.currentTarget.value)}
                    disabled={loading}
                  />

                  <Button
                    type="submit"
                    fullWidth
                    size="md"
                    loading={loading}
                    disabled={!isPasswordValid || !passwordsMatch}
                    style={{
                      background: "linear-gradient(135deg, #ff8c00 0%, #ff6b00 100%)",
                    }}
                  >
                    Create Account
                  </Button>
                </Stack>
              </form>

              <Text ta="center" size="sm">
                Already have an account?{" "}
                <Link
                  to="/login"
                  style={{
                    color: "#ff8c00",
                    fontWeight: "bold",
                    textDecoration: "none",
                  }}
                >
                  Sign in
                </Link>
              </Text>
            </Stack>
          </Box>
        </Stack>
      </Container>
    </Box>
  );
}
