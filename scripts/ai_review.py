#!/usr/bin/env python3
"""AI PR review via a custom endpoint.

Reads a PR diff + metadata, sends them to the configured AI endpoint,
writes the model's review to a markdown file and flags the workflow output.

Env:
  AI_REVIEW_URL    (secret)   full endpoint URL, e.g. https://api.example.com/v1/chat/completions
  AI_REVIEW_KEY    (secret)   API key / bearer token (optional if endpoint is keyless)
  AI_REVIEW_MODEL  (variable) model name (optional; default "gpt-4o-mini" for openai style)
  AI_API_STYLE     (variable) "openai" (default) | "anthropic" | "raw"
  AI_REVIEW_SYSTEM (variable) custom system prompt (optional)
  AI_MAX_DIFF_CHARS(variable) diff truncation limit (optional, default 120000)
  PR_NUMBER                   PR number (for the footer link)
"""
import json
import os
import sys
import urllib.request
import urllib.error

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(errors="replace")

DEFAULT_SYSTEM = (
    "You are a meticulous code reviewer. Review the pull request diff below. "
    "Report: (1) bugs and logic errors, (2) security issues, (3) performance concerns, "
    "(4) style/maintainability notes. Be specific: cite file names and line numbers from the diff. "
    "If the code looks fine, say so briefly. Answer in Russian. Use GitHub-flavored Markdown. "
    "Do not include any preamble about being an AI."
)


def env(name, default=""):
    v = os.environ.get(name)
    return v.strip() if isinstance(v, str) and v.strip() else default


def build_payload(style, system, user_content, model):
    if style == "anthropic":
        return {
            "model": model or "claude-sonnet-4-5",
            "max_tokens": 4096,
            "system": system,
            "messages": [{"role": "user", "content": user_content}],
        }
    if style == "raw":
        return {"prompt": system + "\n\n" + user_content}
    # openai-compatible (default)
    return {
        "model": model or "gpt-4o-mini",
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user_content},
        ],
        "temperature": 0.2,
    }


def extract_text(style, raw_body):
    try:
        data = json.loads(raw_body)
    except json.JSONDecodeError:
        return raw_body.strip()  # plain-text endpoint
    if style == "anthropic":
        parts = data.get("content") or []
        text = "".join(p.get("text", "") for p in parts if isinstance(p, dict))
        return text.strip() or raw_body.strip()
    if isinstance(data, dict):
        choices = data.get("choices")
        if choices:
            msg = choices[0].get("message") or {}
            return (msg.get("content") or choices[0].get("text") or "").strip()
        for key in ("output", "response", "result", "text", "completion"):
            if isinstance(data.get(key), str):
                return data[key].strip()
    return raw_body.strip()


def call_endpoint(url, key, style, payload):
    body = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if key:
        if style == "anthropic":
            headers["x-api-key"] = key
            headers["anthropic-version"] = "2023-06-01"
        else:
            headers["Authorization"] = "Bearer " + key
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    last_err = None
    for attempt in (1, 2):
        try:
            with urllib.request.urlopen(req, timeout=300) as resp:
                return resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:800]
            last_err = "HTTP %s: %s" % (e.code, detail)
            if e.code < 500 and e.code != 429:
                break
        except Exception as e:  # noqa: BLE001
            last_err = "%s: %s" % (type(e).__name__, e)
        if attempt == 1:
            import time
            time.sleep(5)
    raise RuntimeError(last_err or "unknown error")


def main():
    diff_path, meta_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
    gh_output = os.environ.get("GITHUB_OUTPUT")

    def done(reviewed):
        if gh_output:
            with open(gh_output, "a", encoding="utf-8") as f:
                f.write("reviewed=%s\n" % ("true" if reviewed else "false"))

    url = env("AI_REVIEW_URL")
    with open(out_path, "w", encoding="utf-8") as f:
        if not url:
            f.write(
                "## \U0001F916 AI Review\n\n"
                "\u26A0\uFE0F Not configured: set repo **Secret** `AI_REVIEW_URL` "
                "(and `AI_REVIEW_KEY` if your endpoint needs a key) in "
                "Settings \u2192 Secrets and variables \u2192 Actions.\n"
            )
            done(True)
            return 0

    with open(diff_path, encoding="utf-8", errors="replace") as f:
        diff = f.read()
    try:
        with open(meta_path, encoding="utf-8") as f:
            meta = json.load(f)
    except Exception:  # noqa: BLE001
        meta = {}

    if not diff.strip():
        print("Empty diff, skipping review.")
        done(False)
        return 0

    limit = int(env("AI_MAX_DIFF_CHARS", "120000"))
    truncated = ""
    if len(diff) > limit:
        diff = diff[:limit]
        truncated = "\n\n[NOTE: diff truncated to first %d chars]" % limit

    title = meta.get("title", "(no title)")
    pr_body = (meta.get("body") or "").strip()[:2000]
    sha = (meta.get("headRefOid") or "")[:10]
    style = env("AI_API_STYLE", "openai").lower()
    system = env("AI_REVIEW_SYSTEM", DEFAULT_SYSTEM)
    model = env("AI_REVIEW_MODEL")

    user_content = (
        "Pull request: %s\n\n"
        "PR description:\n%s\n\n"
        "Unified diff:\n```diff\n%s\n```%s"
        % (title, pr_body or "(empty)", diff, truncated)
    )

    try:
        raw = call_endpoint(url, env("AI_REVIEW_KEY"), style,
                            build_payload(style, system, user_content, model))
        review_text = extract_text(style, raw)
        if not review_text:
            raise RuntimeError("empty response from endpoint")
    except Exception as e:  # noqa: BLE001
        with open(out_path, "w", encoding="utf-8") as f:
            f.write("## \U0001F916 AI Review\n\n\u274C Review failed: `%s`\n" % str(e)[:1000])
        done(True)
        print("AI review failed:", e)
        return 1

    server = env("GITHUB_SERVER_URL", "https://github.com")
    repo = env("GITHUB_REPOSITORY", "")
    run_id = env("GITHUB_RUN_ID", "")
    pr_no = env("PR_NUMBER", "")
    footer_bits = []
    if model:
        footer_bits.append("model: `%s`" % model)
    if sha:
        footer_bits.append("commit `%s`" % sha)
    if repo and run_id:
        pr_link = "%s/%s/pull/%s" % (server, repo, pr_no) if pr_no else ""
        footer_bits.append("[workflow run](%s/%s/actions/runs/%s)" % (server, repo, run_id))
        if pr_link:
            footer_bits.insert(0, "PR: %s" % pr_link)
    footer = " \u00B7 ".join(footer_bits)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("## \U0001F916 AI Review\n\n%s\n\n---\n<sub>%s</sub>\n" % (review_text, footer))
    done(True)
    print("Review written to", out_path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
