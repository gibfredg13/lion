import { Box, Button, Container, Group, PasswordInput, Stack, Text, TextInput, Title, Alert } from "@mantine/core";
import { useEffect, useState } from "react";
import { useNavigate, Link } from "react-router-dom";

/**
 * Mirrors `merlin.event.allowedEmailDomains` on the server so the form can say no immediately
 * instead of after a round trip. The server is still the authority - this is only a courtesy.
 */
const ALLOWED_EMAIL_DOMAIN = "ing.com";

const isCompanyEmail = (value: string) => {
  const at = value.trim().toLowerCase().lastIndexOf("@");
  if (at <= 0) return false;
  return value.trim().toLowerCase().slice(at + 1) === ALLOWED_EMAIL_DOMAIN;
};

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
  const [accessCode, setAccessCode] = useState("");
  /**
   * Whether the organisers are gating registration on a code. Starts true and stays true if the
   * lookup fails: a network blip must not quietly open registration to anyone who loads the page.
   */
  const [accessCodeRequired, setAccessCodeRequired] = useState(true);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetch("/api/event-config")
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (d && typeof d.accessCodeRequired === "boolean") setAccessCodeRequired(d.accessCodeRequired);
      })
      .catch(() => {
        // Left required. See the note on the state above.
      });
  }, []);

  const passwordStrength = PASSWORD_REQUIREMENTS.filter((req) => req.re.test(password));
  const isPasswordValid = passwordStrength.length === PASSWORD_REQUIREMENTS.length;
  const passwordsMatch = password === confirmPassword && password.length > 0;

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (accessCodeRequired && !accessCode.trim()) {
      setError("Event Access Code is required");
      return;
    }

    if (!isCompanyEmail(email)) {
      setError(`Registration is limited to @${ALLOWED_EMAIL_DOMAIN} email addresses`);
      return;
    }

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
        body: JSON.stringify({ email, displayName, password, accessCode }),
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
        // dvh, not vh: on iOS/Android the URL bar makes 100vh taller than the visible viewport,
        // which pushed the submit button off the bottom of the screen.
        minHeight: "100dvh",
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
              🦁 The Lion's Den
            </Title>
            <Text c="white" size="lg">
              Join the Challenge
            </Text>
          </Box>

          <Box
            p={{ base: "md", sm: "xl" }}
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
                  {/* Hidden entirely when the organisers have switched the gate off, so nobody
                      wonders whether they were meant to have been given a code. */}
                  {accessCodeRequired && (
                    <Box>
                      <TextInput
                        label="Event Access Code"
                        placeholder="Enter the code provided at the event"
                        required
                        value={accessCode}
                        onChange={(e) => setAccessCode(e.currentTarget.value)}
                        disabled={loading}
                      />
                      <Text size="xs" c="dimmed" mt={4}>
                        Ask the event organizer for the access code
                      </Text>
                    </Box>
                  )}

                  <TextInput
                    label="Email"
                    placeholder={`your.name@${ALLOWED_EMAIL_DOMAIN}`}
                    description={`Your @${ALLOWED_EMAIL_DOMAIN} work address`}
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.currentTarget.value)}
                    error={
                      email.trim() && !isCompanyEmail(email)
                        ? `Must be an @${ALLOWED_EMAIL_DOMAIN} address`
                        : null
                    }
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

                  {/*
                    * Placed at the point of consent rather than at the top of the page, where it
                    * would be scrolled past. The event runs a screen in the room: the leaderboard
                    * is always on it, and the organisers can switch on a live feed of attempts -
                    * so what someone types to Leo may be read by their colleagues, under their own
                    * name. That is worth knowing before you agree to it, not after.
                    */}
                  <Alert color="orange" variant="light" title="This is played on a screen in the room">
                    <Text size="sm">
                      Your display name, level and time appear on the event leaderboard. The
                      organisers can also put a live feed of attempts on screen, which shows what
                      you ask Leo and how he answers, next to your name.
                    </Text>
                  </Alert>

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
