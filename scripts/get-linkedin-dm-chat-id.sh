#!/usr/bin/env bash
# Discover Telegram chat/channel id for LinkedinDm bot.
# 1. Add @LinkedinDmBot as admin to your LinkedinDm channel
# 2. Post any message in that channel
# 3. Run this script and copy jobemailer.linkedin-dm-chat-id into .env
set -euo pipefail
cd "$(dirname "$0")/.."

TOKEN=$(grep '^jobemailer.linkedin-dm-bot-token=' .env 2>/dev/null | cut -d= -f2- || true)
if [[ -z "$TOKEN" ]]; then
  echo "Set jobemailer.linkedin-dm-bot-token in .env first." >&2
  exit 1
fi

echo "Fetching recent updates for @LinkedinDmBot..."
curl -sS "https://api.telegram.org/bot${TOKEN}/getUpdates" | python3 -c "
import json, sys
data = json.load(sys.stdin)
if not data.get('ok'):
    print('API error:', data, file=sys.stderr)
    sys.exit(1)
results = data.get('result', [])
if not results:
    print('No updates yet. Post a message in your LinkedinDm channel, then run this again.')
    sys.exit(0)
seen = set()
for u in results:
    for key in ('channel_post', 'message', 'my_chat_member'):
        msg = u.get(key)
        if not msg:
            continue
        chat = msg.get('chat') or msg.get('sender_chat') or {}
        cid = chat.get('id')
        title = chat.get('title') or chat.get('username') or chat.get('first_name') or '?'
        if cid and cid not in seen:
            seen.add(cid)
            print(f'chat_id={cid}  ({title})')
print()
print('Add to .env:  jobemailer.linkedin-dm-chat-id=<chat_id>')
"
