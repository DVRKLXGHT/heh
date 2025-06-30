import requests
import time
from datetime import datetime, timedelta, timezone

# === CONFIG ===
TELEGRAM_BOT_TOKEN = "8181037750:AAFhrsLUMzoLzPbvnlgMnHPKlrJH3leUiCM"
TELEGRAM_CHAT_ID = "7738504985"
THRESHOLD_PERCENT = 7
TIME_WINDOW_MINUTES = 5
SLEEP_INTERVAL = 60  # seconds

price_history = {}

def send_telegram_alert(symbol, percent_change, direction, source):
    emoji = "🚀" if direction == "pump" else "📉"
    message = f"{emoji} {symbol} is {direction.upper()}ING on {source}!\n{percent_change:+.2f}% in last {TIME_WINDOW_MINUTES} mins!"
    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = {"chat_id": TELEGRAM_CHAT_ID, "text": message}
    try:
        response = requests.post(url, data=payload)
        if response.status_code != 200:
            print(f"⚠️ Telegram error: {response.text}")
    except Exception as e:
        print("⚠️ Failed to send Telegram message:", e)

def get_bybit_prices():
    url = "https://api.bybit.com/v5/market/tickers?category=linear"
    try:
        response = requests.get(url)
        data = response.json()
        tickers = data.get("result", {}).get("list", [])
        prices = {}
        for t in tickers:
            symbol = t["symbol"]
            if "USDT" in symbol and not any(x in symbol for x in ["1000", "500", "250"]):
                prices[symbol] = float(t["lastPrice"])
        return prices
    except Exception as e:
        print("⚠️ Bybit API error:", e)
        return {}

def get_blofin_prices():
    url = "https://api.blofin.com/v1/market/tickers"
    try:
        response = requests.get(url)
        data = response.json()
        tickers = data.get("data", [])
        prices = {}
        for t in tickers:
            symbol = t.get("symbol", "")
            price = float(t.get("price", 0))
            if "USDT" in symbol:
                prices[symbol] = price
        return prices
    except Exception as e:
        print("⚠️ Blofin API error:", e)
        return {}

def process_prices(prices, source):
    now = datetime.now(timezone.utc)
    for symbol, current_price in prices.items():
        key = f"{source}:{symbol}"
        if key not in price_history:
            price_history[key] = []
        price_history[key].append((now, current_price))

        # Filter old data
        price_history[key] = [
            (t, p) for t, p in price_history[key]
            if now - t <= timedelta(minutes=TIME_WINDOW_MINUTES)
        ]

        if len(price_history[key]) >= 2:
            old_time, old_price = price_history[key][0]
            if old_price > 0:
                percent_change = (current_price - old_price) / old_price * 100
                if percent_change >= THRESHOLD_PERCENT:
                    send_telegram_alert(symbol, percent_change, "pump", source)
                    price_history[key] = [(now, current_price)]
                elif percent_change <= -THRESHOLD_PERCENT:
                    send_telegram_alert(symbol, percent_change, "dump", source)
                    price_history[key] = [(now, current_price)]

print("✅ Bot started. Watching Bybit & Blofin USDT coins for PUMPS & DUMPS (±7% in 5 mins)...")

while True:
    process_prices(get_bybit_prices(), "Bybit")
    process_prices(get_blofin_prices(), "Blofin")
    time.sleep(SLEEP_INTERVAL)
