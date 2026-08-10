# hackmerlin.io

## Project Structure

- **backend**: Contains the Spring Boot application.
- **frontend**: Contains the React application.

## Getting Started

This project supports multiple LLM backends.

### Supported Providers

- **Azure OpenAI** (default)
- **Google Gemini**
- **Local Ollama**

### Configuration

Set the following properties in `application.yml`:

```yaml
merlin:
  llm:
    provider: azure|gemini|ollama
    apiKey: your-api-key
    baseUrl: your-base-url
    defaultModel: gpt-3.5-turbo
    advancedModel: gpt-4
  passwords:
    - YourPassword1
    - YourPassword2
```

### Azure OpenAI Setup

```yaml
merlin:
  llm:
    provider: azure
    apiKey: your-azure-openai-api-key
    baseUrl: https://your-resource.openai.azure.com
    defaultModel: your-deployment-name-gpt35
    advancedModel: your-deployment-name-gpt4
```

### Google Gemini Setup

```yaml
merlin:
  llm:
    provider: gemini
    apiKey: your-gemini-api-key
    defaultModel: gemini-1.5-pro
    advancedModel: gemini-1.5-pro
```

Obtain a Gemini API key: https://ai.google.dev/

### Local Ollama Setup

```yaml
merlin:
  llm:
    provider: ollama
    baseUrl: http://localhost:11434
    defaultModel: llama2
    advancedModel: llama2
```

**Prerequisites:**
- Install Ollama: https://ollama.ai
- Run: `ollama run llama2` (or any supported model)

## Running the Application

Start the application:

```sh
./gradlew run
```

and visit `http://localhost:8080`
