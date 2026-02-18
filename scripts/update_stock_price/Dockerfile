FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY update_stock_prices.py .

ENTRYPOINT ["python", "update_stock_prices.py"]
