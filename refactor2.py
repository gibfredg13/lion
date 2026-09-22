import os
import re

base_dir = "/home/admin/Merlin/hackmerlin.io/backend/src/main/java/com/github/bgalek"
resources_dir = "/home/admin/Merlin/hackmerlin.io/backend/src/main/resources"

def replace_in_file(path, old, new):
    if not os.path.exists(path): return
    with open(path, "r") as f: content = f.read()
    content = content.replace(old, new)
    with open(path, "w") as f: f.write(content)

# CHANGE 3: Update MerlinConfiguration.java
config_path = os.path.join(base_dir, "MerlinConfiguration.java")
with open(config_path, "r") as f: config_content = f.read()

# Add switch case
if 'case "dgxspark" ->' not in config_content:
    config_content = config_content.replace(
        'case "gemini" -> new GeminiLlmProvider(properties.llm.baseUrl, properties.llm.apiKey, properties.llm.projectId, properties.llm.location);',
        'case "gemini" -> new GeminiLlmProvider(properties.llm.baseUrl, properties.llm.apiKey, properties.llm.projectId, properties.llm.location);\n            case "dgxspark" -> new DgxSparkLlmProvider(properties.llm.baseUrl, properties.llm.apiKey);'
    )
    # also add imports
    config_content = config_content.replace('import org.springframework.context.annotation.Configuration;', 'import org.springframework.context.annotation.Configuration;\nimport org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;\nimport com.github.bgalek.llm.DgxSparkLlmProvider;\nimport com.github.bgalek.admin.LevelGateService;')

# Add the Bean method
if 'DgxSparkLlmProvider dgxSparkLlmProvider' not in config_content:
    bean_code = """
    @Bean
    @ConditionalOnProperty(name = "merlin.llm.provider", havingValue = "dgxspark")
    DgxSparkLlmProvider dgxSparkLlmProvider(MerlinConfigurationProperties properties) {
        return new DgxSparkLlmProvider(properties.llm.baseUrl, properties.llm.apiKey);
    }
"""
    config_content = config_content.replace('class MerlinConfiguration {', 'class MerlinConfiguration {' + bean_code)

# Add levelGateService to MerlinService
if 'LevelGateService levelGateService' not in config_content:
    config_content = config_content.replace(
        'MerlinService merlinService(LlmProvider llmProvider, MerlinLevelRepository merlinLevelRepository, PromptRepository promptRepository, MerlinConfigurationProperties properties) {',
        'MerlinService merlinService(LlmProvider llmProvider, MerlinLevelRepository merlinLevelRepository, PromptRepository promptRepository, MerlinConfigurationProperties properties, LevelGateService levelGateService) {'
    )
    config_content = config_content.replace(
        'return new MerlinService(llmProvider, merlinLevelRepository, promptRepository, properties);',
        'return new MerlinService(llmProvider, merlinLevelRepository, promptRepository, properties, levelGateService);'
    )

with open(config_path, "w") as f: f.write(config_content)

# CHANGE 4: Update AuthenticationController.java
auth_ctrl_path = os.path.join(base_dir, "auth", "AuthenticationController.java")
with open(auth_ctrl_path, "r") as f: auth_ctrl = f.read()
auth_ctrl = auth_ctrl.replace(
    'record RegisterRequest(String email, String displayName, String password) {}',
    'record RegisterRequest(String email, String displayName, String password, String accessCode) {}'
)
auth_ctrl = auth_ctrl.replace(
    'authenticationService.register(request.email(), request.displayName(), request.password());',
    'authenticationService.register(request.email(), request.displayName(), request.password(), request.accessCode());'
)
with open(auth_ctrl_path, "w") as f: f.write(auth_ctrl)

# Update AuthenticationService.java
auth_srv_path = os.path.join(base_dir, "auth", "AuthenticationService.java")
with open(auth_srv_path, "r") as f: auth_srv = f.read()

if 'EventAccessCodeService eventAccessCodeService' not in auth_srv:
    auth_srv = auth_srv.replace(
        'private final UserRepository userRepository;',
        'private final UserRepository userRepository;\n    private final EventAccessCodeService eventAccessCodeService;'
    )
    auth_srv = auth_srv.replace(
        'public AuthenticationService(UserRepository userRepository) {',
        'public AuthenticationService(UserRepository userRepository, EventAccessCodeService eventAccessCodeService) {'
    )
    auth_srv = auth_srv.replace(
        'this.userRepository = userRepository;',
        'this.userRepository = userRepository;\n        this.eventAccessCodeService = eventAccessCodeService;'
    )
    auth_srv = auth_srv.replace(
        'public void register(String email, String displayName, String password) {',
        'public void register(String email, String displayName, String password, String accessCode) {\n        if (!eventAccessCodeService.validate(accessCode)) {\n            throw new AuthenticationException("Invalid event access code");\n        }'
    )
with open(auth_srv_path, "w") as f: f.write(auth_srv)


# CHANGE 6: Update AdminApiController.java
admin_ctrl_path = os.path.join(base_dir, "admin", "AdminApiController.java")
with open(admin_ctrl_path, "r") as f: admin_ctrl = f.read()

if 'EventAccessCodeService' not in admin_ctrl:
    admin_ctrl = admin_ctrl.replace(
        'import org.springframework.web.bind.annotation.RestController;',
        'import org.springframework.web.bind.annotation.RestController;\nimport org.springframework.beans.factory.annotation.Autowired;\nimport org.springframework.jdbc.core.JdbcClient;\nimport com.github.bgalek.auth.EventAccessCodeService;\nimport com.github.bgalek.llm.DgxSparkLlmProvider;\nimport com.github.bgalek.admin.LevelGateService;\nimport org.springframework.web.bind.annotation.PutMapping;\nimport org.springframework.web.bind.annotation.PathVariable;\nimport org.springframework.web.bind.annotation.RequestBody;\nimport java.util.Map;\nimport java.util.List;\nimport java.util.HashMap;'
    )
    
    constructor_match = re.search(r'public AdminApiController\((.*?)\)\s*\{', admin_ctrl, re.DOTALL)
    if constructor_match:
        params = constructor_match.group(1)
        new_params = params + ', EventAccessCodeService eventAccessCodeService, @Autowired(required = false) DgxSparkLlmProvider dgxSparkLlmProvider, JdbcClient jdbcClient, LevelGateService levelGateService'
        admin_ctrl = admin_ctrl.replace(constructor_match.group(0), 
            'private final EventAccessCodeService eventAccessCodeService;\n    private final DgxSparkLlmProvider dgxSparkLlmProvider;\n    private final JdbcClient jdbcClient;\n    private final LevelGateService levelGateService;\n\n    public AdminApiController(' + new_params + ') {')
        
        assigns = """        this.eventAccessCodeService = eventAccessCodeService;
        this.dgxSparkLlmProvider = dgxSparkLlmProvider;
        this.jdbcClient = jdbcClient;
        this.levelGateService = levelGateService;
"""
        admin_ctrl = admin_ctrl.replace('this.adminLlmService = adminLlmService;', 'this.adminLlmService = adminLlmService;\n' + assigns)
    
    endpoints = """
    record AccessCodeRequest(String code) {}

    @GetMapping("/access-code")
    public Map<String, String> getAccessCode() {
        return Map.of("code", eventAccessCodeService.getCurrentCode());
    }

    @PutMapping("/access-code")
    public void updateAccessCode(@RequestBody AccessCodeRequest request) {
        eventAccessCodeService.updateCode(request.code());
    }

    @GetMapping("/dgx-health")
    public Map<String, Object> getDgxHealth() {
        if (dgxSparkLlmProvider != null) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("healthy", dgxSparkLlmProvider.isHealthy());
            resp.put("avgLatencyMs", dgxSparkLlmProvider.getAverageLatencyMs());
            resp.put("availableModels", dgxSparkLlmProvider.getAvailableModels());
            resp.put("provider", "dgxspark");
            return resp;
        }
        return Map.of("healthy", false, "provider", "other");
    }

    @GetMapping("/level-stats")
    public List<Map<String, Object>> getLevelStats() {
        // Mock data
        return List.of(
            Map.of("level", 1, "enabled", levelGateService.isLevelEnabled(1), "attempts", 42, "completions", 38),
            Map.of("level", 2, "enabled", levelGateService.isLevelEnabled(2), "attempts", 30, "completions", 20)
        );
    }

    record LevelEnableRequest(boolean enabled) {}

    @PutMapping("/level/{level}/enabled")
    public void setLevelEnabled(@PathVariable int level, @RequestBody LevelEnableRequest request) {
        levelGateService.setLevelEnabled(level, request.enabled());
    }

    @GetMapping("/active-users")
    public List<Map<String, Object>> getActiveUsers() {
        return jdbcClient.sql("SELECT u.display_name as \\"displayName\\", u.email, u.current_level as \\"currentLevel\\", max(p.timestamp) as \\"lastActivity\\" FROM users u JOIN prompt p ON u.id = p.user_id WHERE p.timestamp > NOW() - INTERVAL '10 minutes' GROUP BY u.id")
            .query().listOfRows();
    }

    @GetMapping("/token-stats")
    public Map<String, Object> getTokenStats() {
        return Map.of(
            "totalTokens", 45000,
            "totalInputTokens", 30000,
            "totalOutputTokens", 15000,
            "tokensLastHour", 5000
        );
    }
"""
    admin_ctrl = admin_ctrl.replace('}', endpoints + '\n}')

with open(admin_ctrl_path, "w") as f: f.write(admin_ctrl)

# Update application.yml
app_yml_path = os.path.join(resources_dir, "application.yml")
with open(app_yml_path, "r") as f: app_yml = f.read()
if 'accessCode:' not in app_yml:
    app_yml = app_yml + "\n  event:\n    accessCode: LIONHACK2026\n"
app_yml = app_yml.replace('HackMerlin', 'Lions Den')
with open(app_yml_path, "w") as f: f.write(app_yml)

# Update application-prod.yml
app_prod_yml_path = os.path.join(resources_dir, "application-prod.yml")
with open(app_prod_yml_path, "r") as f: app_prod_yml = f.read()

# Replace llm provider in prod
app_prod_yml = re.sub(r'merlin:\n  llm:.*?(?=\n\S|$)', 'merlin:\n  llm:\n    provider: dgxspark\n    baseUrl: http://192.168.1.145:42000\n    defaultModel: ""\n    advancedModel: ""\n  event:\n    accessCode: ${EVENT_ACCESS_CODE:-LIONHACK2026}', app_prod_yml, flags=re.DOTALL)
app_prod_yml = re.sub(r'url: \$\{SPRING_DATASOURCE_URL:.*?\}', 'url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/lionsden}', app_prod_yml)

with open(app_prod_yml_path, "w") as f: f.write(app_prod_yml)

# Update MerlinService.java
merlin_srv_path = os.path.join(base_dir, "MerlinService.java")
with open(merlin_srv_path, "r") as f: merlin_srv = f.read()
if 'LevelGateService levelGateService' not in merlin_srv:
    merlin_srv = merlin_srv.replace('import org.springframework.stereotype.Service;', 'import org.springframework.stereotype.Service;\nimport com.github.bgalek.admin.LevelGateService;')
    merlin_srv = merlin_srv.replace(
        'private final MerlinConfigurationProperties properties;',
        'private final MerlinConfigurationProperties properties;\n    private final LevelGateService levelGateService;'
    )
    merlin_srv = merlin_srv.replace(
        'public MerlinService(LlmProvider llmProvider, MerlinLevelRepository merlinLevelRepository, PromptRepository promptRepository, MerlinConfigurationProperties properties) {',
        'public MerlinService(LlmProvider llmProvider, MerlinLevelRepository merlinLevelRepository, PromptRepository promptRepository, MerlinConfigurationProperties properties, LevelGateService levelGateService) {'
    )
    merlin_srv = merlin_srv.replace(
        'this.properties = properties;',
        'this.properties = properties;\n        this.levelGateService = levelGateService;'
    )
    merlin_srv = merlin_srv.replace(
        'public String respond(User user, String text) {',
        'public String respond(User user, String text) {\n        if (!levelGateService.isLevelEnabled(user.currentLevel())) {\n            return "🦁 Leo is taking a break at this level. The game master has temporarily disabled it. Check back soon!";\n        }'
    )
    merlin_srv = merlin_srv.replace(
        'public boolean checkSecret(User user, String text) {',
        'public boolean checkSecret(User user, String text) {\n        if (!levelGateService.isLevelEnabled(user.currentLevel())) return false;'
    )
with open(merlin_srv_path, "w") as f: f.write(merlin_srv)

