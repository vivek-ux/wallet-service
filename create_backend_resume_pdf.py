from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, HRFlowable, KeepTogether
from xml.sax.saxutils import escape

OUT = '/Users/viveknegi/Downloads/wallet-service/Vivek_Negi_Backend_Resume.pdf'
NAVY = colors.HexColor('#17324D')
INK = colors.HexColor('#18232E')
MUTED = colors.HexColor('#425466')
RULE = colors.HexColor('#B8C7D1')
LINK = '#145B8C'

styles = getSampleStyleSheet()
name = ParagraphStyle('Name', parent=styles['Normal'], fontName='Helvetica-Bold', fontSize=16, leading=17.5, textColor=NAVY, alignment=TA_CENTER, spaceAfter=1)
contact = ParagraphStyle('Contact', parent=styles['Normal'], fontName='Helvetica', fontSize=7.2, leading=8.5, textColor=MUTED, alignment=TA_CENTER, spaceAfter=2.5)
section = ParagraphStyle('Section', parent=styles['Normal'], fontName='Helvetica-Bold', fontSize=9.2, leading=10.4, textColor=NAVY, spaceBefore=4.1, spaceAfter=1.8)
body = ParagraphStyle('Body', parent=styles['Normal'], fontName='Helvetica', fontSize=8.25, leading=9.7, textColor=INK, spaceAfter=1.4)
body_compact = ParagraphStyle('BodyCompact', parent=body, fontSize=8.05, leading=9.35, spaceAfter=0.85)
role = ParagraphStyle('Role', parent=styles['Normal'], fontName='Helvetica-Bold', fontSize=8.15, leading=9.45, textColor=INK, spaceBefore=0.4, spaceAfter=0.75)
project = ParagraphStyle('Project', parent=role, spaceBefore=1)
skill = ParagraphStyle('Skill', parent=body_compact, spaceAfter=0.35)

link = lambda label, url: f'<link href="{escape(url)}" color="{LINK}">{escape(label)}</link>'

def sec(title):
    return [Paragraph(title, section), HRFlowable(width='100%', thickness=0.55, color=RULE, spaceBefore=0, spaceAfter=3)]

def bullet(text):
    return Paragraph('- ' + text, body_compact)

story = [
    Paragraph('VIVEK NEGI', name),
    Paragraph(
        f'Dehradun, Uttarakhand | {link("8077481802", "tel:+918077481802")} | '
        f'{link("viveknegi177@gmail.com", "mailto:viveknegi177@gmail.com")} | '
        f'{link("linkedin.com/in/vivek-negi-b46070222", "https://linkedin.com/in/vivek-negi-b46070222")} | '
        f'{link("github.com/vivek-ux", "https://github.com/vivek-ux")}', contact),
]

story += sec('PROFESSIONAL SUMMARY')
story.append(Paragraph(
    'Backend Software Engineer with nearly 2 years of enterprise production support and operations experience at HSBC Technology India. Currently pursuing an M.Tech in CSE at NIT Meghalaya. Specializing in Java 17, Spring Boot, PostgreSQL, Redis, Kafka, and REST APIs, with expertise in transaction safeguards, database locking, and asynchronous event streaming.', body))

story += sec('CORE SKILLS')
for line in [
    '<b>Languages:</b> Java 17, Python, SQL',
    '<b>Backend:</b> Spring Boot, REST APIs, Microservices, Spring Security, JWT, FastAPI, Pydantic',
    '<b>Databases and caching:</b> PostgreSQL, BigQuery, Redis Caching, SQL query optimization',
    '<b>Cloud and messaging:</b> GCP, Cloud Scheduler, Apache Kafka',
    '<b>Distributed systems:</b> Pessimistic Locking, Redis TTL Idempotency, Transactional Outbox Pattern',
    '<b>Testing and tools:</b> JUnit 5, Mockito, Integration Testing, Docker, Git, Maven, Postman, CI/CD',
]: story.append(Paragraph(line, skill))

story += sec('PROFESSIONAL EXPERIENCE')
story.append(Paragraph('<b>HSBC Technology India</b> — Software Engineer, Production Support &amp; Operations | Pune, Maharashtra | Aug 2023 – Jun 2025', role))
for text in [
    'Monitored and triaged 15+ multi-node End-of-Day (EOD) batch processing jobs daily on GCP and Cloud Scheduler, tracking execution to achieve 100% processing deadline compliance.',
    'Executed and optimized 20+ complex SQL and BigQuery queries against datasets containing millions of rows for enterprise financial reporting and data troubleshooting.',
    'Investigated 30+ L2/L3 production incidents using centralized GCP log analysis and failure-pattern identification, coordinating hotfix validation and cross-team deployments.',
    'Maintained 10+ Cloud Scheduler and BigQuery pipeline integrations for daily automated data ingestion and downstream executive reporting.',
]: story.append(bullet(text))

story += sec('PROJECTS')
story.append(Paragraph(
    f'<b>Distributed Fintech Wallet Service</b> | Java 17, Spring Boot, PostgreSQL, Redis, Apache Kafka | '
    f'{link("github.com/vivek-ux/wallet-service", "https://github.com/vivek-ux/wallet-service")}', project))
for text in [
    'Engineered 6+ REST API endpoints for user authentication, wallet operations, money transfers, and audit history using Spring Boot, Spring Security, and JWT.',
    'Prevented race conditions and double-spending across 100% of concurrent transfers using Spring @Transactional boundaries and ordered pessimistic row-level locking (SELECT FOR UPDATE).',
    'Implemented Redis TTL idempotency keys with a 24-hour expiration window to prevent duplicate transaction execution across network retries.',
    'Architected a database-backed Transactional Outbox Pattern with retry logic to ensure 100% reliable, at-least-once Kafka event delivery.',
    'Authored 15+ unit and integration tests using JUnit 5 and Mockito, simulating concurrent transfers, duplicate requests, validation errors, and transaction-history responses.',
]: story.append(bullet(text))

story.append(Paragraph('<b>PolicyQA - Insurance Document Q&amp;A API</b> | Python, FastAPI, Pydantic, OpenAI API, NumPy, Vector Search', project))
for text in [
    'Built a document Q&amp;A API processing multi-page policy PDFs using semantic text chunking, OpenAI embeddings, and similarity search across vector stores.',
    'Implemented validated API requests and answers with explicit source references using FastAPI and Pydantic, reducing missing context errors across 10+ sample insurance policies.',
    'Evaluated and handled edge cases, including unsupported user queries, maintaining strict fallback error handling to prevent AI model hallucinations.',
]: story.append(bullet(text))

story += sec('EDUCATION')
story.append(Paragraph('<b>M.Tech, Computer Science and Engineering</b> - National Institute of Technology Meghalaya | Expected 2027', body_compact))
story.append(Paragraph('<b>B.Tech, Computer Science and Engineering</b> - Graphic Era University | 2023', body_compact))

story += sec('ACHIEVEMENTS')
story.append(Paragraph('Recommended by AFSB Varanasi for Flying Officer position (Indian Air Force AFCAT 1, 2024).', body_compact))

doc = SimpleDocTemplate(
    OUT, pagesize=A4,
    rightMargin=12*mm, leftMargin=12*mm, topMargin=7*mm, bottomMargin=7*mm,
    title='Vivek Negi - Backend Software Engineer Resume',
    author='Vivek Negi',
    subject='ATS-readable backend engineering resume',
)
doc.build(story)
print(OUT)
