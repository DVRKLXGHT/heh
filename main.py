import requests
import time

# === CONFIG ===
DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1389535288371449927/SrWU4KPpcIV8cchgaQzo-8NwTsfpvBtE3HgLCQWl6BJNTdygCs1ixJM6A8Oc1v6e0DLu"
PRICE_CHANGE_THRESHOLD = 5.0  # ±5% price change for 5m candle
SLEEP_INTERVAL = 60  # check every 60 seconds

# === SEND ALERT ===
def send_discord_alert(symbol, change, direction):
    emoji = "🚀" if direction == "pump" else "📉"
    message = f"@everyone {emoji} **{symbol}** 5m candle closed with {change:+.2f}% ({direction.upper()}) on **Bybit**!"

    try:
        requests.post(DISCORD_WEBHOOK_URL, json={"content": message})
    except Exception as e:
        print("❌ Discord error:", e)

# === FETCH BYBIT 5m CANDLE DATA ===
def get_bybit_data():
    symbols_url = "https://api.bybit.com/v5/market/tickers?category=linear"
    kline_url = "https://api.bybit.com/v5/market/kline?category=linear&interval=5&limit=3"
    alerts = {}

    try:
        r = requests.get(symbols_url)
        data = r.json().get("result", {}).get("list", [])
        symbols = [s["symbol"] for s in data if "USDT" in s["symbol"] and not any(x in s["symbol"] for x in ["1000", "500"])]

        for symbol in symbols:
            k_res = requests.get(f"{kline_url}&symbol={symbol}")
            k_data = k_res.json().get("result", {}).get("list", [])
            if len(k_data) >= 3:
                o = float(k_data[-2][1])  # Open price of last closed candle
                c = float(k_data[-2][4])  # Close price of last closed candle
                change = ((c - o) / o) * 100
                alerts[symbol] = change
    except Exception as e:
        print("Bybit error:", e)

    return alerts

# === CHECK ALERTS ===
def check_alerts(data):
    for symbol, change in data.items():
        if change >= PRICE_CHANGE_THRESHOLD:
            send_discord_alert(symbol, change, "pump")
        elif change <= -PRICE_CHANGE_THRESHOLD:
            send_discord_alert(symbol, change, "dump")

# === MAIN LOOP ===
print("✅ Bot running... watching Bybit 5m candles for ±5% price moves")
while True:
    bybit_data = get_bybit_data()
    check_alerts(bybit_data)
    time.sleep(SLEEP_INTERVAL)
import requests
import time

# === CONFIG ===
DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1389535288371449927/SrWU4KPpcIV8cchgaQzo-8NwTsfpvBtE3HgLCQWl6BJNTdygCs1ixJM6A8Oc1v6e0DLu"
PRICE_CHANGE_THRESHOLD = 5.0  # ±5% price change for 5m candle
SLEEP_INTERVAL = 60  # check every 60 seconds

# === SEND ALERT ===
def send_discord_alert(symbol, change, direction):
    emoji = "🚀" if direction == "pump" else "📉"
    message = f"@everyone {emoji} **{symbol}** 5m candle closed with {change:+.2f}% ({direction.upper()}) on **Bybit**!"

    try:
        requests.post(DISCORD_WEBHOOK_URL, json={"content": message})
    except Exception as e:
        print("❌ Discord error:", e)

# === FETCH BYBIT 5m CANDLE DATA ===
def get_bybit_data():
    symbols_url = "https://api.bybit.com/v5/market/tickers?category=linear"
    kline_url = "https://api.bybit.com/v5/market/kline?category=linear&interval=5&limit=3"
    alerts = {}

    try:
        r = requests.get(symbols_url)
        data = r.json().get("result", {}).get("list", [])
        symbols = [s["symbol"] for s in data if "USDT" in s["symbol"] and not any(x in s["symbol"] for x in ["1000", "500"])]

        for symbol in symbols:
            k_res = requests.get(f"{kline_url}&symbol={symbol}")
            k_data = k_res.json().get("result", {}).get("list", [])
            if len(k_data) >= 3:
                o = float(k_data[-2][1])  # Open price of last closed candle
                c = float(k_data[-2][4])  # Close price of last closed candle
                change = ((c - o) / o) * 100
                alerts[symbol] = change
    except Exception as e:
        print("Bybit error:", e)

    return alerts

# === CHECK ALERTS ===
def check_alerts(data):
    for symbol, change in data.items():
        if change >= PRICE_CHANGE_THRESHOLD:
            send_discord_alert(symbol, change, "pump")
        elif change <= -PRICE_CHANGE_THRESHOLD:
            send_discord_alert(symbol, change, "dump")

# === MAIN LOOP ===
print("✅ Bot running... watching Bybit 5m candles for ±5% price moves")
while True:
    bybit_data = get_bybit_data()
    check_alerts(bybit_data)
    time.sleep(SLEEP_INTERVAL)
