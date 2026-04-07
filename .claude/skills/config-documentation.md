# Config Documentation Skill

When working with configuration files in this project:

## Rules

1. **Keep config.yaml.example concise**
   - Use brief inline comments only
   - No multi-line documentation blocks
   - No detailed instructions on how to create tokens or configure services
   - Example: `token: "op://Private/GitHub/token"  # 1Password reference` is OK
   - Example: Multi-paragraph explanations of token creation are NOT OK

2. **Put detailed documentation in README.md**
   - Token creation instructions
   - Configuration options explanations
   - Multi-instance setup guides
   - Troubleshooting tips
   - All detailed "how-to" content

3. **README.md sections for config docs**
   - "Configuration" section for basic setup
   - "Credential Management" section for tokens and secrets
   - "Advanced Configuration" section for complex setups

## Rationale

- config.yaml.example should be a quick reference template
- Detailed docs in README.md are easier to search, link to, and maintain
- Users copy config.yaml.example to their config directory; verbose comments clutter their config
