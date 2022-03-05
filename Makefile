.phony: default build java cpp

default: build

build: java cpp

java: java/*.java
	(cd java; javac *.java)

cpp: cpp/stepping cpp/stepping_thread

cpp/stepping: cpp/stepping.cpp
	(cd cpp; c++ -O3 -o stepping stepping.cpp)

cpp/stepping_thread: cpp/stepping.cpp cpp/stepping_thread.cpp
	(cd cpp; c++ -pthread -O3 -o stepping_thread stepping_thread.cpp)
