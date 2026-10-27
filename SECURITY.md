# Security policy / 安全问题报告

## Report privately

Project repositories: [backend atom-crm](https://github.com/Archetom/atom-crm) and [frontend atom-crm-web](https://github.com/Archetom/atom-crm-web). Private reporting availability depends on the repository settings.

Do not disclose exploitable vulnerabilities in public issues or pull requests. On the repository hosting this project, use **Security → Report a vulnerability** when private vulnerability reporting is enabled. If that option is unavailable, open a public issue asking only for a private reporting channel; omit the vulnerability details, payload, affected tenant/customer information, and reproduction steps until a private channel is established.

Provide the affected repository and commit/version, a minimal reproduction using synthetic data, expected and actual behavior, and the potential impact. Report suspected tenant-isolation bypass, authorization bypass, credential exposure, unsafe file access, or dependency vulnerabilities through the same private channel. Never send production passwords, session cookies, reset tokens, signing keys, database dumps, or real customer records.

Maintainers assess reports against the current code and coordinate disclosure after a fix or mitigation is available. This repository does not currently promise a response SLA or a security-backport window for older releases. Report the exact affected version rather than assuming an old release receives fixes.

## Deployment responsibilities

Follow the [configuration and security guide](docs/configuration.md) and the [release scope](release-R1.md). Keep production signing keys and service credentials outside source control; use HTTPS and the same-origin session/CSRF configuration. Development identity headers and synthetic adapters are for local testing. Optional integrations remain unavailable until their documented runtime prerequisites are satisfied.

A source scan or passing test suite does not establish that a deployment is secure. Before publishing a repository, maintainers must also review Git history and release assets for secrets and private data, enable a private reporting channel on the hosting service, and confirm the contact route works. Exposed credentials must be revoked and replaced; removing them from the current checkout is insufficient.

## 中文说明

前后端仓库地址见上方链接；私密报告入口以实际仓库设置为准。请勿在公开 Issue 或 PR 中披露可利用的漏洞。优先使用目标仓库 **Security → Report a vulnerability** 的私密报告入口；若该入口尚未启用，可仅公开请求建立私密渠道，不附漏洞细节或复现步骤。建立私密渠道后，提供受影响版本/提交号、使用合成数据的最小复现和影响说明，不发送生产凭据或真实客户数据。

目前未承诺安全响应时限或旧版本补丁维护周期。开源发布前，维护者还需在托管平台启用并验证私密报告入口；此文件本身不会更改平台设置。
