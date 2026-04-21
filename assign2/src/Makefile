JAVAC := javac
JAVA  := java

SRC_DIR := ./
BIN_DIR := ../bin

SERVER_CLASS := server.ChatServer
CLIENT_CLASS := client.ChatClient

SOURCES := $(shell find $(SRC_DIR) -name "*.java")

HOST ?= localhost
PORT ?= 9900

.PHONY: all build server client clean

all: build

build:
	mkdir -p $(BIN_DIR)
	$(JAVAC) -d $(BIN_DIR) $(SOURCES)

server: build
	$(JAVA) -cp $(BIN_DIR) $(SERVER_CLASS) $(PORT)

client: build
	$(JAVA) -cp $(BIN_DIR) $(CLIENT_CLASS) $(HOST) $(PORT)

clean:
	rm -rf $(BIN_DIR)