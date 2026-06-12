const tokenStorageKey = "walletServiceToken";

const elements = {
    accountForm: document.querySelector("#accountForm"),
    balanceValue: document.querySelector("#balanceValue"),
    historyButton: document.querySelector("#historyButton"),
    loginForm: document.querySelector("#loginForm"),
    logoutButton: document.querySelector("#logoutButton"),
    message: document.querySelector("#message"),
    refreshButton: document.querySelector("#refreshButton"),
    registerForm: document.querySelector("#registerForm"),
    sessionState: document.querySelector("#sessionState"),
    transactionRows: document.querySelector("#transactionRows"),
    transferForm: document.querySelector("#transferForm")
};

function getToken() {
    return localStorage.getItem(tokenStorageKey);
}

function setToken(token) {
    localStorage.setItem(tokenStorageKey, token);
    updateSessionState();
}

function clearToken() {
    localStorage.removeItem(tokenStorageKey);
    updateSessionState();
    elements.balanceValue.textContent = "--";
    elements.transactionRows.innerHTML = '<tr><td colspan="6">No transactions loaded</td></tr>';
}

function updateSessionState() {
    const token = getToken();
    elements.sessionState.textContent = token ? `Token: ${token}` : "Signed out";
}

function showMessage(text, isError = false) {
    elements.message.textContent = text;
    elements.message.classList.toggle("error", isError);
}

function formData(form) {
    return Object.fromEntries(new FormData(form).entries());
}

async function request(path, options = {}) {
    const headers = {
        "Content-Type": "application/json",
        ...options.headers
    };

    const token = getToken();

    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(path, {
        ...options,
        headers
    });

    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const message = typeof body === "string" ? body : body.message;
        throw new Error(message || "Request failed");
    }

    return body;
}

function money(value) {
    if (value === null || value === undefined || value === "") {
        return "--";
    }

    return Number(value).toLocaleString(undefined, {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

function idempotencyKey() {
    if (crypto.randomUUID) {
        return crypto.randomUUID();
    }

    return `transfer-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function refreshBalance() {
    const balance = await request("/accounts/me/balance");
    elements.balanceValue.textContent = money(balance);
}

async function refreshHistory() {
    const transactions = await request("/accounts/me/transactions");

    if (transactions.length === 0) {
        elements.transactionRows.innerHTML = '<tr><td colspan="6">No transactions yet</td></tr>';
        return;
    }

    elements.transactionRows.innerHTML = transactions.map(transaction => `
        <tr>
            <td>${transaction.id}</td>
            <td>${transaction.fromEmail}</td>
            <td>${transaction.toEmail}</td>
            <td>${money(transaction.amount)}</td>
            <td>${transaction.status}</td>
            <td>${transaction.createdAt ? new Date(transaction.createdAt).toLocaleString() : "--"}</td>
        </tr>
    `).join("");
}

async function refreshDashboard() {
    await Promise.all([
        refreshBalance(),
        refreshHistory()
    ]);
}

elements.registerForm.addEventListener("submit", async event => {
    event.preventDefault();
    showMessage("Creating user...");

    try {
        const data = formData(event.currentTarget);
        const message = await request("/auth/register", {
            method: "POST",
            body: JSON.stringify(data)
        });
        showMessage(message);
        event.currentTarget.reset();
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    showMessage("Logging in...");

    try {
        const data = formData(event.currentTarget);
        const token = await request("/auth/login", {
            method: "POST",
            body: JSON.stringify(data)
        });
        setToken(token);
        showMessage("Logged in");
        event.currentTarget.reset();
        await refreshDashboard();
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.accountForm.addEventListener("submit", async event => {
    event.preventDefault();
    showMessage("Creating account...");

    try {
        const data = formData(event.currentTarget);
        const message = await request("/accounts/create", {
            method: "POST",
            body: JSON.stringify({
                initialBalance: data.initialBalance
            })
        });
        showMessage(message);
        event.currentTarget.reset();
        await refreshDashboard();
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.transferForm.addEventListener("submit", async event => {
    event.preventDefault();
    showMessage("Sending transfer...");

    try {
        const data = formData(event.currentTarget);
        const message = await request("/accounts/transfer", {
            method: "POST",
            headers: {
                "Idempotency-Key": data.idempotencyKey || idempotencyKey()
            },
            body: JSON.stringify({
                toEmail: data.toEmail,
                amount: data.amount
            })
        });
        showMessage(message);
        event.currentTarget.reset();
        await refreshDashboard();
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.refreshButton.addEventListener("click", async () => {
    try {
        await refreshDashboard();
        showMessage("Dashboard refreshed");
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.historyButton.addEventListener("click", async () => {
    try {
        await refreshHistory();
        showMessage("Transactions refreshed");
    } catch (error) {
        showMessage(error.message, true);
    }
});

elements.logoutButton.addEventListener("click", () => {
    clearToken();
    showMessage("Logged out");
});

updateSessionState();

if (getToken()) {
    refreshDashboard().catch(error => showMessage(error.message, true));
}
