"""Repository root for the design generator. Override with ETAYE_ROOT if the folder moves."""
import os
ROOT = os.environ.get("ETAYE_ROOT") or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Design System sources: ds/project in the working tree, design-system/ in the exported ZIP.
DS = os.path.join(ROOT, "ds", "project") if os.path.isdir(os.path.join(ROOT, "ds", "project")) else os.path.join(ROOT, "design-system")
