# Authentication

Cookie Launcher supports three account types. They are separate code paths on purpose:
their failure modes share nothing, and collapsing them produces the vague error messages
users complain about everywhere else.

---

## 1. Offline accounts

No network, no credentials. The UUID is derived with Mojang's own rule:

```
UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(UTF_8))
```

This is the exact identity every server in offline mode assigns, so worlds and inventories
match what the official launcher would produce for the same name. Case matters: `Steve` and
`steve` are different identities, just as they are on a real server.

Offline accounts play singleplayer and LAN. They cannot join servers that run in online mode.

---

## 2. authlib-injector (Yggdrasil)

For third-party authentication servers (so-called "skin servers" and private auth
providers). Cookie Launcher downloads `authlib-injector`, logs in over the classic Yggdrasil
endpoints, and patches the agent into the JVM:

```
-javaagent:<cache>/authlib/authlib-injector-<version>.jar=<server-root-url>
```

Required endpoints on the server:

| Endpoint | Used for |
|---|---|
| `POST /authserver/authenticate` | Username and password login |
| `POST /authserver/refresh` | Token renewal |
| `GET /api/yggdrasil` | Server metadata and display name |
| `GET /api/user/profile/<uuid>` | Skin listing |

The server root URL is what you type in the Accounts dialog, e.g.
`https://example.com/api/yggdrasil`. The launcher sends it unchanged to authlib-injector,
because the agent expects the full API root rather than the bare host.

Tokens are stored in `accounts.json` under the launcher's data directory and are never
transmitted anywhere except back to the server that issued them. `Account.toString()`
deliberately omits both tokens so they cannot leak into a log line.

---

## 3. Microsoft sign-in (dormant by default)

The full Entra device-code chain is implemented:

```
device code  ->  poll for tokens  ->  Xbox Live  ->  XSTS  ->  Minecraft  ->  profile
```

**No client id is embedded in this repository.** Shipping someone else's application id
would be both a licence problem and a ban risk, so each distributor registers their own.

### Registering your own application

1. Go to <https://entra.microsoft.com> and sign in with an account that has a tenant.
   *A personal Microsoft account cannot register applications — that is an Entra
   limitation, not a launcher bug. A free Microsoft 365 Developer tenant or an Azure
   account works.*
2. **App registrations → New registration**.
3. Supported account types: **Personal Microsoft accounts only**.
4. **Authentication → Allow public client flows: Yes** (required for device code).
5. Copy the **Application (client) ID**.
6. Add it to `config.json` in the launcher's data directory:

```json
{ "msaClientId": "00000000-0000-0000-0000-000000000000" }
```

No client secret and no redirect URI are needed: device code flow uses neither.

### What the launcher does with it

| Step | Endpoint | Result |
|---|---|---|
| 1 | `login.microsoftonline.com/consumers/oauth2/v2.0/devicecode` | user code and verification URL |
| 2 | same host, `.../token` | access and refresh tokens |
| 3 | `user.auth.xboxlive.com/user/authenticate` | Xbox Live token |
| 4 | `xsts.auth.xboxlive.com/xsts/authorize` | XSTS token and user hash |
| 5 | `api.minecraftservices.com/authentication/login_with_xbox` | Minecraft access token |
| 6 | `api.minecraftservices.com/minecraft/profile` | name and UUID, or a clear "does not own the game" error |

A refresh token is stored so the account survives restarts. When a token is rejected the
launcher reports that specific case rather than a generic failure.

---

## 4. How accounts reach the game

`LaunchBuilder` fills the documented placeholder set:

| Placeholder | Offline | authlib | Microsoft |
|---|---|---|---|
| `auth_player_name` | username | profile name | profile name |
| `auth_uuid` | derived | profile id | profile id |
| `auth_access_token` | `0` | session token | Minecraft token |
| `auth_session` | `0` | session token | Minecraft token |
| `user_type` | `legacy` | `msa` | `msa` |
| `auth_xuid` | `0` | `0` | `0` |

`--accessToken` is never empty: some versions reject an empty value even in offline mode,
so `0` is passed instead.

---

## 5. Threat model

| Threat | Mitigation |
|---|---|
| Token leakage through logs | `Account.toString()` omits tokens; the launch command display redacts `--accessToken` |
| Token leakage through config sharing | Tokens live in `accounts.json`, never in `config.json` |
| Malicious mirror serving modified jars | Every download is verified against the SHA-1 published in Mojang metadata |
| Malicious mod code | Not sandboxed in 1.0 (same as every desktop launcher); documented as a known limitation |
| Credential phishing | The launcher never asks for a Microsoft password; device code sends the user to Microsoft's own page |

The launcher never sends analytics, never uploads logs, and never contacts a server the user
did not configure.
