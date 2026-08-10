import { Box, Button, Container, PasswordInput, Stack, Text, TextInput, Title, Alert } from "@mantine/core";
import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const response = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.message || "Login failed");
      }

      navigate("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
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
              🏦 HackMerlin
            </Title>
            <Text c="white" size="lg">
              ING Security Challenge
            </Text>
          </Box>

          <Box
            bg="white"
            p="xl"
            radius="lg"
            style={{
              boxShadow: "0 10px 40px rgba(0,0,0,0.2)",
            }}
          >
            <Stack gap="md">
              <Title order={2} size="h3" ta="center">
                Login
              </Title>

              {error && (
                <Alert color="red" title="Error">
                  {error}
                </Alert>
              )}

              <form onSubmit={handleLogin}>
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

                  <PasswordInput
                    label="Password"
                    placeholder="Enter your password"
                    required
                    value={password}
                    onChange={(e) => setPassword(e.currentTarget.value)}
                    disabled={loading}
                  />

                  <Button
                    type="submit"
                    fullWidth
                    size="md"
                    loading={loading}
                    style={{
                      background: "linear-gradient(135deg, #ff8c00 0%, #ff6b00 100%)",
                    }}
                  >
                    Sign In
                  </Button>
                </Stack>
              </form>

              <Text ta="center" size="sm">
                Don't have an account?{" "}
                <Link
                  to="/register"
                  style={{
                    color: "#ff8c00",
                    fontWeight: "bold",
                    textDecoration: "none",
                  }}
                >
                  Sign up
                </Link>
              </Text>
            </Stack>
          </Box>
        </Stack>
      </Container>
    </Box>
  );
}
