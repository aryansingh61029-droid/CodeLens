// Public examples only; user submissions are never stored here.
window.CODE_SAMPLES = {
  "JAVA": [
    "class Baseline {\n    static int sumEven(int[] values) {\n        int total = 0;\n        for (int value : values) {\n            if (value % 2 == 0) {\n                total += value;\n            }\n        }\n        return total;\n    }\n}",
    "// New names, same structure.\nclass Renamed {\n  static int addSelected(int[] numbers) {\n    int result = 0;\n    for (int item : numbers) {\n      if (item % 2 == 0) {\n        result += item;\n      }\n    }\n    return result;\n  }\n}",
    "abstract class Different {\n    private String message;\n    protected Different(String message) {\n        this.message = message;\n    }\n    abstract void display();\n    public synchronized void dispatch() {\n        try {\n            display();\n        } catch (IllegalStateException failure) {\n            throw new RuntimeException(message, failure);\n        }\n    }\n}"
  ],
  "PYTHON": [
    "def sum_even(values):\n    total = 0\n    for value in values:\n        if value % 2 == 0:\n            total += value\n    return total\n",
    "# Renamed variables and a new comment.\ndef add_selected(numbers):\n    result = 0\n    for item in numbers:\n        if item % 2 == 0:\n            result += item\n    return result\n",
    "class MessageStore:\n    def __init__(self, message):\n        self.message = message\n\n    async def dispatch(self, consumer):\n        try:\n            await consumer.send(self.message)\n        except RuntimeError as failure:\n            raise ValueError(\"delivery failed\") from failure\n"
  ],
  "CPP": [
    "int sumEven(const int values[], int size) {\n    int total = 0;\n    for (int index = 0; index < size; index++) {\n        if (values[index] % 2 == 0) {\n            total += values[index];\n        }\n    }\n    return total;\n}",
    "// Same computation with new names.\nint addSelected(const int numbers[], int length) {\n  int result = 0;\n  for (int cursor = 0; cursor < length; cursor++) {\n    if (numbers[cursor] % 2 == 0) {\n      result += numbers[cursor];\n    }\n  }\n  return result;\n}",
    "class MessageStore {\nprivate:\n    const char* message;\npublic:\n    explicit MessageStore(const char* value) : message(value) {}\n    virtual ~MessageStore() = default;\n    virtual void display() = 0;\n    void dispatch() {\n        try {\n            display();\n        } catch (...) {\n            throw;\n        }\n    }\n};"
  ]
};
