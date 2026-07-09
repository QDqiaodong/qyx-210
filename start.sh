#!/bin/bash

set -e

echo "=========================================="
echo "  内河航运码头候船座椅航线绑定统计系统"
echo "=========================================="

FRONTEND_PORT=$(grep FRONTEND_PORT .env | cut -d'=' -f2)
SERVER_PORT=$(grep SERVER_PORT .env | cut -d'=' -f2)

echo ""
echo "启动服务..."
echo ""

docker compose up -d --build

echo ""
echo "等待服务启动..."
sleep 30

echo ""
echo "=========================================="
echo "服务启动完成！"
echo ""
echo "前端访问地址: http://localhost:${FRONTEND_PORT}"
echo "后端API地址: http://localhost:${SERVER_PORT}"
echo "=========================================="