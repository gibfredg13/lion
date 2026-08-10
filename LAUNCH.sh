#!/bin/bash

# HackMerlin Launch Script
# Real-Time Guardrail Breach Detection System

set -e

echo ""
echo "╔════════════════════════════════════════════════════════════════════════════╗"
echo "║                   🚀 HACKMERLIN STARTUP WIZARD                             ║"
echo "║              Real-Time Guardrail Breach Detection System                  ║"
echo "╚════════════════════════════════════════════════════════════════════════════╝"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# STEP 1: Check Prerequisites
# ============================================================================
echo -e "${BLUE}📋 STEP 1: Checking prerequisites...${NC}"
echo ""

JAVA_INSTALLED=false
NODE_INSTALLED=false
NPM_INSTALLED=false

# Check Java
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    echo -e "${GREEN}✓${NC} Java installed: $JAVA_VERSION"
    JAVA_INSTALLED=true
else
    echo -e "${YELLOW}⚠${NC} Java NOT found in PATH"
    # Try to find it
    if [ -f "/usr/libexec/java_home" ]; then
        JAVA_HOME=$(/usr/libexec/java_home)
        export JAVA_HOME
        export PATH="$JAVA_HOME/bin:$PATH"
        echo -e "${GREEN}✓${NC} Found Java at: $JAVA_HOME"
        JAVA_INSTALLED=true
    fi
fi

# Check Node
if command -v node &> /dev/null; then
    NODE_VERSION=$(node -v)
    echo -e "${GREEN}✓${NC} Node.js installed: $NODE_VERSION"
    NODE_INSTALLED=true
else
    echo -e "${RED}✗${NC} Node.js NOT installed"
fi

# Check npm
if command -v npm &> /dev/null; then
    NPM_VERSION=$(npm -v)
    echo -e "${GREEN}✓${NC} npm installed: $NPM_VERSION"
    NPM_INSTALLED=true
else
    echo -e "${RED}✗${NC} npm NOT installed"
fi

echo ""

if [ "$JAVA_INSTALLED" = false ] || [ "$NODE_INSTALLED" = false ] || [ "$NPM_INSTALLED" = false ]; then
    echo -e "${RED}❌ MISSING PREREQUISITES${NC}"
    echo ""
    echo "Required:"
    [ "$JAVA_INSTALLED" = false ] && echo "  • Java 17+ → Install from https://www.oracle.com/java/technologies/downloads/"
    [ "$NODE_INSTALLED" = false ] && echo "  • Node.js 18+ → Install from https://nodejs.org/"
    [ "$NPM_INSTALLED" = false ] && echo "  • npm → Comes with Node.js"
    echo ""
    exit 1
fi

echo -e "${GREEN}✅ All prerequisites met!${NC}"
echo ""

# ============================================================================
# STEP 2: Install Frontend Dependencies
# ============================================================================
echo -e "${BLUE}📦 STEP 2: Installing frontend dependencies...${NC}"
echo ""

if [ ! -d "frontend/node_modules" ]; then
    echo "Installing npm packages..."
    cd frontend
    npm install
    cd ..
    echo -e "${GREEN}✓${NC} Frontend dependencies installed"
else
    echo -e "${GREEN}✓${NC} Frontend dependencies already installed"
fi

echo ""

# ============================================================================
# STEP 3: Build Backend
# ============================================================================
echo -e "${BLUE}🔨 STEP 3: Building backend...${NC}"
echo ""

if [ ! -f "gradlew" ]; then
    echo -e "${RED}✗${NC} gradlew not found!"
    exit 1
fi

# Make sure gradlew is executable
chmod +x gradlew

echo "Compiling Spring Boot application..."
./gradlew clean build -x test --info 2>&1 | tail -20

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Backend built successfully"
else
    echo -e "${RED}✗${NC} Backend build failed"
    exit 1
fi

echo ""

# ============================================================================
# STEP 4: Start Application
# ============================================================================
echo -e "${BLUE}🚀 STEP 4: Starting HackMerlin...${NC}"
echo ""

JAR_FILE=$(find build/libs -name "*.jar" -type f | head -1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}✗${NC} JAR file not found in build/libs/"
    exit 1
fi

echo "JAR file: $JAR_FILE"
echo ""

# Configuration
export MERLIN_LLM_PROVIDER=${MERLIN_LLM_PROVIDER:-ollama}
export MERLIN_LLM_PORT=${MERLIN_LLM_PORT:-8080}

echo -e "${YELLOW}Configuration:${NC}"
echo "  • LLM Provider: $MERLIN_LLM_PROVIDER"
echo "  • Server Port: $MERLIN_LLM_PORT"
echo ""

echo -e "${GREEN}📍 Application starting...${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

java -jar "$JAR_FILE" \
    --server.port="$MERLIN_LLM_PORT" \
    --merlin.llm.provider="$MERLIN_LLM_PROVIDER"

# Note: Process continues - use Ctrl+C to stop

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${YELLOW}ℹ Application stopped${NC}"
