from functools import lru_cache
from pathlib import Path

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter


POLICY_PATH = Path(__file__).resolve().parents[1] / "policies" / "wallet_risk_policy.md"
CHROMA_DIR = Path(__file__).resolve().parents[1] / ".chroma"
COLLECTION_NAME = "wallet-risk-policy"


def _load_policy_document() -> list[Document]:
    text = POLICY_PATH.read_text(encoding="utf-8")
    return [Document(page_content=text, metadata={"source": str(POLICY_PATH.name)})]


@lru_cache(maxsize=1)
def get_vector_store() -> Chroma:
    embeddings = OpenAIEmbeddings()
    splitter = RecursiveCharacterTextSplitter(chunk_size=700, chunk_overlap=120)
    chunks = splitter.split_documents(_load_policy_document())

    return Chroma.from_documents(
        documents=chunks,
        embedding=embeddings,
        collection_name=COLLECTION_NAME,
        persist_directory=str(CHROMA_DIR),
    )


def retrieve_policy_context(query: str, k: int = 4) -> str:
    vector_store = get_vector_store()
    documents = vector_store.similarity_search(query, k=k)

    return "\n\n".join(
        f"Source: {document.metadata.get('source', 'policy')}\n{document.page_content}"
        for document in documents
    )
