#include <iostream>
#include <thread>
#include <mutex>
#include <vector>

#define STEPPING_THREAD 1
std::mutex g_stepping_mutex;

#include "stepping.cpp"

using namespace std;
typedef vector<thread*> VT;

int main() {
  int num_threads = thread::hardware_concurrency();
  cout << "0 threads " << num_threads << endl;

  VT v;
  for (int i = 0; i < num_threads; i++) {
    v.push_back(new thread(stepping_main));
  }
  for (int i = 0; i < num_threads; i++) {
    v[i]->join();
  }

  stepping_end();

}
