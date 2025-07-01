import requests
import time

# === CONFIG ===
DISCORD_WEBHOOK_URL = "https://discord.com/api/webhooks/1389535288371449927/SrWU4KPpcIV8cchgaQzo-8NwTsfpvBtE3HgLCQWl6BJNTdygCs1ixJM6A8Oc1v6e0DLu"
PRICE_CHANGE_THRESHOLD = 3.7  # ±% for 5m candle
VOLUME_SPIKE_MULTIPLIER = 150.0  # volume x on 1h candle
SLEEP_INTERVAL = 60

def send_discord_alert(symbol, value, event_type, source):
    emoji = {
        "pump": "🚀",
        "dump": "📉",
        "volume": "📊"
    }.get(event_type, "⚠️")

    if event_type == "volume":
        message = f"@everyone {emoji} **{symbol}** 1H volume spiked by {value:.2f}× on **{source}**"
    else:
        message = f"@everyone {emoji} **{symbol}** 5m candle closed with {value:+.2f}% ({event_type.upper()}) on **{source}**!"

    try:
        requests.post(DISCORD_WEBHOOK_URL, json={"content": message})
    except Exception as e:
        print("❌ Discord error:", e)

# --- 5m PRICE CHECK ---
def get_price_data(symbols, source):
    url_bybit = "https://api.bybit.com/v5/market/kline?category=linear&interval=5&limit=3"
    url_blofin = "https://api.blofin.com/v1/market/kline?interval=5m&limit=3&symbol={}"

    results = {}
    for symbol in symbols:
        try:
            if source == "Bybit":
                res = requests.get(f"{url_bybit}&symbol={symbol}")
                k = res.json().get("result", {}).get("list", [])
            else:
                res = requests.get(url_blofin.format(symbol))
                k = res.json().get("data", [])

            if len(k) >= 3:
                o = float(k[-2][1])
                c = float(k[-2][4])
                change = ((c - o) / o) * 100
                results[symbol] = change
        except Exception:
            continue
    return results

# --- 1h VOLUME CHECK ---
def get_volume_data(symbols, source):
    url_bybit = "https://api.bybit.com/v5/market/kline?category=linear&interval=60&limit=3"
    url_blofin = "https://api.blofin.com/v1/market/kline?interval=1h&limit=3&symbol={}"

    results = {}
    for symbol in symbols:
        try:
            if source == "Bybit":
                res = requests.get(f"{url_bybit}&symbol={symbol}")
                k = res.json().get("result", {}).get("list", [])
            else:
                res = requests.get(url_blofin.format(symbol))
                k = res.json().get("data", [])

            if len(k) >= 3:
                v1 = float(k[-2][5])
                v2 = float(k[-3][5])
                ratio = v1 / v2 if v2 > 0 else 0
                results[symbol] = ratio
        except Exception:
            continue
    return results

def get_bybit_symbols():
    try:
        r = requests.get("https://api.bybit.com/v5/market/tickers?category=linear")
        data = r.json().get("result", {}).get("list", [])
        return [s["symbol"] for s in data if "USDT" in s["symbol"] and not any(x in s["symbol"] for x in ["1000", "500"])]
    except:
        return []

def get_blofin_symbols():
    try:
        r = requests.get("https://api.blofin.com/v1/market/tickers")
        data = r.json().get("data", [])
        return [s["symbol"] for s in data if "USDT" in s["symbol"]]
    except:
        return []

def process_data():
    # Bybit
    bybit_symbols = get_bybit_symbols()
    bybit_price = get_price_data(bybit_symbols, "Bybit")
    bybit_volume = get_volume_data(bybit_symbols, "Bybit")

    for sym, change in bybit_price.items():
        if change >= PRICE_CHANGE_THRESHOLD:
            send_discord_alert(sym, change, "pump", "Bybit")
        elif change <= -PRICE_CHANGE_THRESHOLD:
            send_discord_alert(sym, change, "dump", "Bybit")

    for sym, ratio in bybit_volume.items():
        if ratio >= VOLUME_SPIKE_MULTIPLIER:
            send_discord_alert(sym, ratio, "volume", "Bybit")

    # Blofin
    blofin_symbols = get_blofin_symbols()
    blofin_price = get_price_data(blofin_symbols, "Blofin")
    blofin_volume = get_volume_data(blofin_symbols, "Blofin")

    for sym, change in blofin_price.items():
        if change >= PRICE_CHANGE_THRESHOLD:
            send_discord_alert(sym, change, "pump", "Blofin")
        elif change <= -PRICE_CHANGE_THRESHOLD:
            send_discord_alert(sym, change, "dump", "Blofin")

    for sym, ratio in blofin_volume.items():
        if ratio >= VOLUME_SPIKE_MULTIPLIER:
            send_discord_alert(sym, ratio, "volume", "Blofin")

# === MAIN LOOP ===
print("✅ Bot running... 5m price change + 1h volume spikes")
while True:
    process_data()
    time.sleep(SLEEP_INTERVAL)
