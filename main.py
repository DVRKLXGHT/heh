
import requests
import time
from datetime import datetime, timedelta

# === CONFIG ===
TELEGRAM_BOT_TOKEN = "8181037750:AAFhrsLUMzoLzPbvnlgMnHPKlrJH3leUiCM"
TELEGRAM_CHAT_ID = "7738504985"
BYBIT_MARKET = "linear"  # USDT pairs
PUMP_THRESHOLD = 7  # %
TIME_WINDOW_MINUTES = 10
SLEEP_INTERVAL = 60  # check every 60 sec

price_history = {}

def send_telegram_alert(symbol, percent_change):
    message = f"🚀 {symbol} is PUMPING!\n+{percent_change:.2f}% in last {TIME_WINDOW_MINUTES} mins!"
    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = {"chat_id": TELEGRAM_CHAT_ID, "text": message}
    try:
        response = requests.post(url, data=payload)
        if response.status_code != 200:
            print(f"⚠️ Telegram error: {response.text}")
    except Exception as e:
        print("⚠️ Failed to send Telegram message:", e)

def get_bybit_prices():
    url = f"https://api.bybit.com/v5/market/tickers?category={BYBIT_MARKET}"
    try:
        response = requests.get(url)
        if response.status_code != 200:
            print(f"⚠️ Bybit API error: {response.status_code}")
            return {}
        if not response.text.strip().startswith('{'):
            print("⚠️ Empty or invalid response from Bybit")
            return {}
        data = response.json()
        tickers = data.get("result", {}).get("list", [])
        prices = {}
        for ticker in tickers:
            symbol = ticker["symbol"]
            if "USDT" in symbol and not any(x in symbol for x in ["1000", "500", "250"]):
                prices[symbol] = float(ticker["lastPrice"])
        return prices
    except Exception as e:
        print(f"⚠️ Failed to get prices from Bybit: {e}")
        return {}

print("✅ Bot started. Watching Bybit USDT coins for 7% pumps...")

while True:
    now = datetime.utcnow()
    prices = get_bybit_prices()

    for symbol, current_price in prices.items():
        if symbol not in price_history:
            price_history[symbol] = []
        price_history[symbol].append((now, current_price))

        # Keep only 10 min of data
        price_history[symbol] = [
            (t, p) for t, p in price_history[symbol]
            if now - t <= timedelta(minutes=TIME_WINDOW_MINUTES)
        ]

        if len(price_history[symbol]) >= 2:
            old_time, old_price = price_history[symbol][0]
            if old_price > 0:
                percent_change = (current_price - old_price) / old_price * 100
                if percent_change >= PUMP_THRESHOLD:
                    send_telegram_alert(symbol, percent_change)
                    price_history[symbol] = [(now, current_price)]  # reset after alert

    time.sleep(SLEEP_INTERVAL)
