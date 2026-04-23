#!/bin/bash
set -e

echo "🔴 Starting AIgeny..."

# Virtual framebuffer
Xvfb :1 -screen 0 ${XVFB_SCREEN:-1280x800x24} -nolisten tcp &
XVFB_PID=$!
export DISPLAY=:1
sleep 1

# Window manager (minimal, dark theme)
fluxbox &
sleep 0.5

# VNC server (no password for simplicity in local Docker - change -nopw to -passwd for security)
x11vnc -display :1 -nopw -listen 0.0.0.0 -rfbport 5900 -xkb -forever -quiet &
sleep 0.5

# noVNC websockify (browser access on port 6080)
websockify --web /usr/share/novnc/ 6080 localhost:5900 &
sleep 0.5

echo "🌐 AIgeny GUI available at: http://localhost:6080/vnc.html"

# Wait for Ollama to be reachable (if configured)
OLLAMA_URL="${AIGENY_LLM_BASE_URL:-}"
if echo "$OLLAMA_URL" | grep -q "ollama"; then
    OLLAMA_HOST=$(echo "$OLLAMA_URL" | sed 's|/v1.*||')
    echo "⏳ Waiting for Ollama at $OLLAMA_HOST ..."
    for i in $(seq 1 30); do
        if curl -sf "$OLLAMA_HOST/api/tags" > /dev/null 2>&1; then
            echo "✅ Ollama is ready!"
            break
        fi
        echo "   ... attempt $i/30"
        sleep 3
    done
fi

echo "🔴 Starting Java application..."

# Launch the Java app
java \
  -Dawt.useSystemAAFontSettings=on \
  -Dswing.aatext=true \
  -Djava.awt.headless=false \
  -Dfile.encoding=UTF-8 \
  -jar /app/aigeny.jar

# Keep container alive if app exits
wait $XVFB_PID

