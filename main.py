import requests
import time

# === CONFIG ===
DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1389535288371449927/SrWU4KPpcIV8cchgaQzo-8NwTsfpvBtE3HgLCQWl6BJNTdygCs1ixJM6A8Oc1v6e0DLu"
PRICE_CHANGE_THRESHOLD = 3.7  # %
VOLUME_SPIKE_MULTIPLIER = 1.5  # spike = current volume is 1.5x previous
SLEEP_INTERVAL = 60  # check every 60 sec

# === ALERT FUNCTION ===
def send_discord_alert(symbol, value, event_type, source):
    emoji = {
        "pump": "🚀",
        "dump": "📉",
        "volume": "📊"
    }.get(event_type, "⚠️")

    if event_type == "volume":
        message = f"{emoji} **{symbol}** volume spiked by {value:.2f}× on **{source}** (5m candle)"
    else:
        message = f"{emoji} **{symbol}** 5m candle closed with {value:+.2f}% ({event_type.upper()}) on **{source}**!"

    try:
        requests.post(DISCORD_WEBHOOK_URL, json={"content": message})
    except Exception as e:
        print("❌ Discord error:", e)

# === FETCH BYBIT CANDLES ===
def get_bybit_data():
    url = "https://api.bybit.com/v5/market/kline?category=linear&interval=5&limit=3"
    symbols_url = "https://api.bybit.com/v5/market/tickers?category=linear"
    alerts = {}

    try:
        data = requests.get(symbols_url).json().get("result", {}).get("list", [])
        symbols = [t["symbol"] for t in data if "USDT" in t["symbol"] and not any(x in t["symbol"] for x in ["1000", "500"])]
        
        for symbol in symbols:
            res = requests.get(f"{url}&symbol={symbol}")
            k = res.json().get("result", {}).get("list", [])
            if len(k) >= 3:
                o = float(k[-2][1])
                c = float(k[-2][4])
                v1 = float(k[-2][5])
                v2 = float(k[-3][5])
                pc = ((c - o) / o) * 100
                vr = v1 / v2 if v2 > 0 else 0
                alerts[symbol] = (pc, vr)
    except Exception as e:
        print("Bybit error:", e)

    return alerts

# === FETCH BLOFIN CANDLES ===
def get_blofin_data():
    tickers = "https://api.blofin.com/v1/market/tickers"
    alerts = {}

    try:
        d = requests.get(tickers).json().get("data", [])
        symbols = [s["symbol"] for s in d if "USDT" in s["symbol"]]
        
        for symbol in symbols:
            url = f"https://api.blofin.com/v1/market/kline?symbol={symbol}&interval=5m&limit=3"
            res = requests.get(url).json()
            k = res.get("data", [])
            if len(k) >= 3:
                o = float(k[-2][1])
                c = float(k[-2][4])
                v1 = float(k[-2][5])
                v2 = float(k[-3][5])
                pc = ((c - o) / o) * 100
                vr = v1 / v2 if v2 > 0 else 0
                alerts[symbol] = (pc, vr)
    except Exception as e:
        print("Blofin error:", e)

    return alerts

# === CHECK FOR EVENTS ===
def check_alerts(data, source):
    for symbol, (pc, vr) in data.items():
        if pc >= PRICE_CHANGE_THRESHOLD:
            send_discord_alert(symbol, pc, "pump", source)
        elif pc <= -PRICE_CHANGE_THRESHOLD:
            send_discord_alert(symbol, pc, "dump", source)
        elif vr >= VOLUME_SPIKE_MULTIPLIER:
            send_discord_alert(symbol, vr, "volume", source)

# === MAIN LOOP ===
print("✅ Bot is running... monitoring 5m pump, dump, volume events")
while True:
    check_alerts(get_bybit_data(), "Bybit")
    check_alerts(get_blofin_data(), "Blofin")
    time.sleep(SLEEP_INTERVAL)
