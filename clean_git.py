import subprocess

def run_git(args):
    try:
        result = subprocess.run(['git'] + args, capture_output=True, text=True)
        print(f"Running: git {' '.join(args)}")
        if result.stderr:
            print(f"Error: {result.stderr}")
        else:
            print(f"Output: {result.stdout}")
    except Exception as e:
        print(f"Exception: {e}")

# Paths to remove from git cache (index)
to_remove = [
    ".gradle",
    "build",
    "app/build",
    "app/src/main/java/com/example",
    "app/src/main/java/com/guberdev/smsync"
]

print("Resetting index...")
run_git(["reset"])

for path in to_remove:
    print(f"Removing {path} from git index...")
    run_git(["rm", "-r", "--cached", path, "--ignore-unmatch"])

print("Restaging files...")
run_git(["add", "."])

print("Final status check...")
run_git(["status"])
