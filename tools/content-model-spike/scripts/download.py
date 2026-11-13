"""Download the two candidate repositories into raw/ (gitignored). Normal TLS checks; no token is read or written."""
import sys
from pathlib import Path

from huggingface_hub import HfApi, snapshot_download

ROOT = Path(__file__).resolve().parent.parent
REPOS = {
    "embeddinggemma-2": "google/embeddinggemma-2",
    "siglip2-base-patch16-224": "google/siglip2-base-patch16-224",
}


def main(names):
    api = HfApi()
    for name in names:
        repo = REPOS[name]
        info = api.model_info(repo, files_metadata=True)
        print(f"{repo}: gated={info.gated} sha={info.sha}")
        for s in info.siblings:
            print(f"  {s.rfilename:40s} {s.size}")
        target = ROOT / "raw" / name
        # Fails with a gated-repo error if the maintainer must accept terms; we then stop that candidate.
        snapshot_download(repo, local_dir=target)
        print("downloaded to", target)


if __name__ == "__main__":
    main(sys.argv[1:] or list(REPOS))
