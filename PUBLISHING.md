# Publishing winsock4j to Maven Central

Releases are published to Maven Central via the [Sonatype Central Portal](https://central.sonatype.com)
using the [Vanniktech Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/).
Publishing runs automatically in GitHub Actions ([`.github/workflows/publish.yml`](.github/workflows/publish.yml))
whenever a **GitHub Release** is created.

- **Group / namespace:** `vet.inpulse` (domain-verified via `inpulse.vet`)
- **Artifact:** `vet.inpulse:winsock4j`

---

## One-time setup

These steps only need to be done once (and the GitHub secrets only re-added if rotated).

### 1. Register the namespace on the Central Portal

1. Sign in at <https://central.sonatype.com> (GitHub login works).
2. Go to **View Namespaces → Add Namespace** and enter `vet.inpulse`.
3. The portal shows a **DNS TXT record** value to prove ownership of `inpulse.vet`.
   Add it as a TXT record on the `inpulse.vet` zone, then click **Verify Namespace**.
   Verification can take a few minutes after the DNS record propagates.

### 2. Generate a Central Portal user token

1. On the Central Portal, open **Account → Generate User Token**.
2. This produces a **username** and **password** pair (these are *not* your login). Copy both —
   the password is shown only once.

### 3. Create a GPG signing key

Maven Central requires every artifact to be GPG-signed.

```bash
# Generate a key (choose RSA 4096, no expiry or a long one; set a passphrase).
gpg --full-generate-key

# Find the key id (the long hex string after "sec   rsa4096/").
gpg --list-secret-keys --keyid-format=long

# Publish the PUBLIC key so Central can verify signatures.
gpg --keyserver keys.openpgp.org --send-keys <KEYID>

# Export the ARMORED PRIVATE key — this whole block (incl. the BEGIN/END lines)
# becomes the SIGNING_KEY secret.
gpg --armor --export-secret-keys <KEYID>
```

### 4. Add the GitHub Actions secrets

In the repo: **Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret | Value |
| --- | --- |
| `MAVEN_CENTRAL_USERNAME` | The Central Portal user token **username** (step 2) |
| `MAVEN_CENTRAL_PASSWORD` | The Central Portal user token **password** (step 2) |
| `SIGNING_KEY`            | The full ASCII-armored private key (step 3, incl. `-----BEGIN/END-----` lines) |
| `SIGNING_KEY_PASSWORD`   | The passphrase you set on the GPG key |

The workflow maps these to the Gradle properties the plugin reads
(`ORG_GRADLE_PROJECT_mavenCentralUsername/Password`, `ORG_GRADLE_PROJECT_signingInMemoryKey/KeyPassword`),
so no secret ever lands in the repository.

---

## Cutting a release

The version is **derived from the Git tag** — there is no version hardcoded in `build.gradle.kts`.
`build.gradle.kts` reads the `VERSION` environment variable, strips a leading `v`, and falls back to
`1.0.0-SNAPSHOT` for local builds:

```kotlin
version = System.getenv("VERSION")?.removePrefix("v")?.ifBlank { null } ?: "1.0.0-SNAPSHOT"
```

To release version `X.Y.Z`:

1. Make sure `master` is in the state you want to publish.
2. On GitHub, **Releases → Draft a new release**.
3. Create a **new tag** `vX.Y.Z` (e.g. `v1.0.0`) targeting `master`, add release notes, and **Publish release**.
4. The `Publish to Maven Central` workflow runs automatically:
   - `actions/setup-java` provisions JDK 25.
   - `./gradlew publishToMavenCentral` assembles the main, `-sources`, and `-javadoc` jars, signs them,
     and uploads the bundle to the Central Portal.
   - Because the plugin is configured with `automaticRelease = true`, the deployment is **released
     automatically** — no manual "Publish" click in the portal UI is needed.
5. Watch the run under the **Actions** tab. After it succeeds, the artifact appears in the Central Portal
   and then on <https://central.sonatype.com> / <https://search.maven.org> (indexing can take a while).

> **Note:** publishing does **not** run the test suite. The tests load `ws2_32` and only pass on Windows;
> `publishToMavenCentral` only assembles and signs jars, so the Linux runner never executes them. This is
> intentional.

---

## Verifying locally before tagging

You can validate the artifacts and POM without any credentials:

```bash
VERSION=1.0.0 ./gradlew publishToMavenLocal --no-configuration-cache
```

Then inspect `~/.m2/repository/vet/inpulse/winsock4j/1.0.0/`. You should see the main jar, `-sources.jar`,
`-javadoc.jar`, and the `.pom`. Open the `.pom` and confirm the `name`, `description`, `url`, license,
`developers`, and `scm` blocks are present — Central rejects POMs missing any of these.

To also exercise signing locally, set the signing env vars first:

```bash
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys <KEYID>)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="<passphrase>"
VERSION=1.0.0 ./gradlew publishToMavenLocal --no-configuration-cache
# Each artifact should now have a matching .asc signature.
```

---

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `401 Unauthorized` on upload | Wrong/expired `MAVEN_CENTRAL_USERNAME`/`PASSWORD`; regenerate the user token and update the secrets. |
| `Namespace ... is not registered` / not verified | The `vet.inpulse` namespace isn't verified yet — finish the DNS TXT verification (step 1). |
| Signing task fails / "no signing key" | `SIGNING_KEY` missing or not the full armored block, or `SIGNING_KEY_PASSWORD` is wrong. Re-export with `gpg --armor --export-secret-keys`. |
| Signature can't be verified by Central | The **public** key wasn't published to a keyserver — re-run `gpg --keyserver keys.openpgp.org --send-keys <KEYID>`. |
| Deployment uploaded but not released | `automaticRelease` was disabled — release it manually in the Central Portal **Deployments** view, or re-enable in `build.gradle.kts`. |
