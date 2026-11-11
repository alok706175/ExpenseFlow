/**
 * ExpenseFlow - Frontend Client Application
 * Vanilla JS, Fetch API, Chart.js Integration
 */

const API_BASE = '/api/transactions';
const BUDGET_API = '/api/budget';

let currentPage = 0;
const pageSize = 10;
let totalPages = 0;

let monthlyChart = null;
let categoryChart = null;

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    initDefaultDates();
    setupEventListeners();
    loadDashboard();
});

function initDefaultDates() {
    const today = new Date();
    const curYear = today.getFullYear();
    const curMonth = String(today.getMonth() + 1).padStart(2, '0');
    const curDay = String(today.getDate()).padStart(2, '0');

    // Default transaction date in modal to today
    const txDateInput = document.getElementById('txDate');
    if (txDateInput) {
        txDateInput.value = `${curYear}-${curMonth}-${curDay}`;
    }

    // Default budget modal month to current month
    const budgetMonthInput = document.getElementById('budgetMonth');
    if (budgetMonthInput) {
        budgetMonthInput.value = `${curYear}-${curMonth}`;
    }
}

function setupEventListeners() {
    // Search input with debounce
    let searchDebounceTimer;
    document.getElementById('filterSearch').addEventListener('input', (e) => {
        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => {
            currentPage = 0;
            loadDashboard();
        }, 350);
    });

    // Filters change triggers reload
    ['filterType', 'filterCategory', 'filterStartDate', 'filterEndDate'].forEach(id => {
        document.getElementById(id).addEventListener('change', () => {
            currentPage = 0;
            loadDashboard();
        });
    });
}

function getFilterParams() {
    const search = document.getElementById('filterSearch').value.trim();
    const type = document.getElementById('filterType').value;
    const category = document.getElementById('filterCategory').value;
    const startDate = document.getElementById('filterStartDate').value;
    const endDate = document.getElementById('filterEndDate').value;

    const params = new URLSearchParams();
    if (search) params.append('search', search);
    if (type) params.append('type', type);
    if (category) params.append('category', category);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);

    return params;
}

async function loadDashboard() {
    await Promise.all([
        fetchSummary(),
        fetchTransactions()
    ]);
}

/**
 * Fetch and Render Financial Summary & Charts
 */
async function fetchSummary() {
    try {
        const filterParams = getFilterParams();
        const response = await fetch(`${API_BASE}/summary?${filterParams.toString()}`);
        if (!response.ok) throw new Error('Failed to load summary');
        const data = await response.json();

        // 1. Update KPI Cards
        document.getElementById('kpiTotalIncome').textContent = formatCurrency(data.totalIncome || 0);
        document.getElementById('kpiTotalExpense').textContent = formatCurrency(data.totalExpense || 0);

        const balanceEl = document.getElementById('kpiBalance');
        const balance = data.balance || 0;
        balanceEl.textContent = formatCurrency(balance);
        balanceEl.className = `text-2xl font-bold tracking-tight ${balance >= 0 ? 'text-slate-900' : 'text-rose-600'}`;

        document.getElementById('kpiTopCategory').textContent = data.topSpendingCategory || 'None';

        // 2. Budget Health Card
        const budgetLimit = data.monthlyBudgetLimit || 0;
        const usedPercent = data.budgetUsedPercentage || 0;
        const budgetBar = document.getElementById('kpiBudgetBar');

        document.getElementById('kpiBudgetLabel').textContent = `Budget: ${formatCurrency(budgetLimit)}`;
        document.getElementById('kpiBudgetPercent').textContent = `${usedPercent}%`;
        budgetBar.style.width = `${Math.min(usedPercent, 100)}%`;

        if (usedPercent >= 100) {
            budgetBar.className = 'bg-rose-500 h-2 rounded-full transition-all duration-500';
        } else if (usedPercent >= 80) {
            budgetBar.className = 'bg-amber-500 h-2 rounded-full transition-all duration-500';
        } else {
            budgetBar.className = 'bg-emerald-500 h-2 rounded-full transition-all duration-500';
        }

        // 3. Budget Alert Banner
        renderBudgetAlert(data);

        // 4. Update Charts
        renderMonthlyTrendChart(data.monthlyTrends || []);
        renderCategoryChart(data.categoryExpenses || {});

    } catch (err) {
        console.error('Error fetching summary:', err);
    }
}

function renderBudgetAlert(summary) {
    const banner = document.getElementById('budgetAlertBanner');
    const icon = document.getElementById('budgetAlertIcon');
    const title = document.getElementById('budgetAlertTitle');
    const text = document.getElementById('budgetAlertText');

    if (summary.alertStatus === 'EXCEEDED') {
        banner.className = 'block rounded-xl p-4 bg-rose-50 border border-rose-200 text-rose-900 animate-fade-in';
        icon.className = 'p-2 rounded-lg bg-rose-100 text-rose-600';
        icon.innerHTML = '<i class="fa-solid fa-triangle-exclamation text-lg"></i>';
        title.textContent = 'Budget Limit Exceeded!';
        text.textContent = summary.alertMessage;
    } else if (summary.alertStatus === 'WARNING') {
        banner.className = 'block rounded-xl p-4 bg-amber-50 border border-amber-200 text-amber-900 animate-fade-in';
        icon.className = 'p-2 rounded-lg bg-amber-100 text-amber-600';
        icon.innerHTML = '<i class="fa-solid fa-bell text-lg"></i>';
        title.textContent = 'Budget Warning (Above 80%)';
        text.textContent = summary.alertMessage;
    } else {
        banner.className = 'hidden';
    }
}

/**
 * Fetch and Render Transactions Table
 */
async function fetchTransactions() {
    const tableBody = document.getElementById('transactionsTableBody');
    try {
        const params = getFilterParams();
        params.append('page', currentPage);
        params.append('size', pageSize);
        params.append('sortBy', 'transactionDate');
        params.append('direction', 'DESC');

        const response = await fetch(`${API_BASE}?${params.toString()}`);
        if (!response.ok) throw new Error('Failed to load transactions');
        const data = await response.json();

        totalPages = data.totalPages;

        // Render Rows
        if (!data.content || data.content.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="7" class="py-12 text-center">
                        <div class="flex flex-col items-center justify-center text-slate-400">
                            <i class="fa-regular fa-folder-open text-4xl mb-3 text-slate-300"></i>
                            <p class="text-sm font-semibold text-slate-600">No transactions found</p>
                            <p class="text-xs text-slate-400 mt-1">Try clearing active filters or add a new entry.</p>
                        </div>
                    </td>
                </tr>`;
        } else {
            tableBody.innerHTML = data.content.map(tx => {
                const isExpense = tx.type === 'EXPENSE';
                const formattedAmount = `${isExpense ? '-' : '+'}${formatCurrency(tx.amount)}`;
                const amountClass = isExpense ? 'text-rose-600' : 'text-emerald-600 font-bold';
                const badgeTypeClass = isExpense ? 'bg-rose-50 text-rose-700 border-rose-200' : 'bg-emerald-50 text-emerald-700 border-emerald-200';

                return `
                    <tr class="hover:bg-slate-50/70 border-b border-slate-100 transition-colors">
                        <td class="py-3.5 pl-6 pr-3 whitespace-nowrap text-slate-500 text-xs font-mono">
                            ${tx.transactionDate}
                        </td>
                        <td class="px-3 py-3.5 text-slate-900 font-semibold max-w-[200px] truncate" title="${escapeHtml(tx.title)}">
                            ${escapeHtml(tx.title)}
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap">
                            <span class="inline-flex items-center px-2 py-0.5 rounded-md text-[11px] font-semibold badge-${escapeHtml(tx.category)}">
                                ${escapeHtml(tx.category)}
                            </span>
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap">
                            <span class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${badgeTypeClass}">
                                ${tx.type}
                            </span>
                        </td>
                        <td class="px-3 py-3.5 whitespace-nowrap font-mono text-sm ${amountClass}">
                            ${formattedAmount}
                        </td>
                        <td class="px-3 py-3.5 text-slate-500 text-xs max-w-[220px] truncate" title="${escapeHtml(tx.notes || '')}">
                            ${escapeHtml(tx.notes || '-')}
                        </td>
                        <td class="py-3.5 pl-3 pr-6 text-right whitespace-nowrap space-x-2">
                            <button onclick="editTransaction(${tx.id})" title="Edit" class="text-slate-400 hover:text-emerald-600 p-1 transition-colors">
                                <i class="fa-regular fa-pen-to-square"></i>
                            </button>
                            <button onclick="deleteTransaction(${tx.id})" title="Delete" class="text-slate-400 hover:text-rose-600 p-1 transition-colors">
                                <i class="fa-regular fa-trash-can"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');
        }

        // Update Pagination controls
        const totalElements = data.totalElements || 0;
        const start = totalElements === 0 ? 0 : currentPage * pageSize + 1;
        const end = Math.min((currentPage + 1) * pageSize, totalElements);
        document.getElementById('paginationInfo').textContent = `Showing ${start} to ${end} of ${totalElements} entries`;
        document.getElementById('pageBadge').textContent = `${currentPage + 1} / ${Math.max(totalPages, 1)}`;

        document.getElementById('prevPageBtn').disabled = currentPage <= 0;
        document.getElementById('nextPageBtn').disabled = currentPage >= totalPages - 1 || totalPages === 0;

    } catch (err) {
        console.error('Error fetching transactions:', err);
        tableBody.innerHTML = `<tr><td colspan="7" class="py-6 text-center text-rose-500 font-semibold text-xs">Failed to load transactions.</td></tr>`;
    }
}

function changePage(delta) {
    const newPage = currentPage + delta;
    if (newPage >= 0 && newPage < totalPages) {
        currentPage = newPage;
        fetchTransactions();
    }
}

function resetFilters() {
    document.getElementById('filterSearch').value = '';
    document.getElementById('filterType').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    currentPage = 0;
    loadDashboard();
}

/**
 * Chart.js Visualizations
 */
function renderMonthlyTrendChart(trends) {
    const ctx = document.getElementById('monthlyTrendChart').getContext('2d');

    const labels = trends.map(t => t.month);
    const incomes = trends.map(t => t.income);
    const expenses = trends.map(t => t.expense);

    if (monthlyChart) {
        monthlyChart.destroy();
    }

    monthlyChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: labels,
            datasets: [
                {
                    label: 'Income',
                    data: incomes,
                    backgroundColor: 'rgba(34, 197, 94, 0.85)',
                    borderRadius: 6,
                    borderSkipped: false
                },
                {
                    label: 'Expense',
                    data: expenses,
                    backgroundColor: 'rgba(244, 63, 94, 0.85)',
                    borderRadius: 6,
                    borderSkipped: false
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${ctx.dataset.label}: ${formatCurrency(ctx.raw)}`
                    }
                }
            },
            scales: {
                x: {
                    grid: { display: false }
                },
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: (value) => `$${value}`
                    },
                    grid: { color: 'rgba(226, 232, 240, 0.6)' }
                }
            }
        }
    });
}

function renderCategoryChart(categoryMap) {
    const ctx = document.getElementById('categoryChart').getContext('2d');

    const labels = Object.keys(categoryMap);
    const data = Object.values(categoryMap);

    if (categoryChart) {
        categoryChart.destroy();
    }

    if (labels.length === 0) {
        // Empty state for doughnut
        categoryChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['No Expenses'],
                datasets: [{
                    data: [1],
                    backgroundColor: ['#e2e8f0']
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false }
                }
            }
        });
        return;
    }

    const palette = [
        '#f59e0b', '#6366f1', '#ec4899', '#10b981',
        '#8b5cf6', '#06b6d4', '#f97316', '#64748b'
    ];

    categoryChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: labels,
            datasets: [{
                data: data,
                backgroundColor: palette.slice(0, labels.length),
                borderWidth: 2,
                borderColor: '#ffffff',
                hoverOffset: 4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '70%',
            plugins: {
                legend: {
                    position: 'bottom',
                    labels: {
                        boxWidth: 10,
                        font: { size: 11 },
                        padding: 12
                    }
                },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ` ${ctx.label}: ${formatCurrency(ctx.raw)}`
                    }
                }
            }
        }
    });
}

/**
 * Modal Handling: Add & Edit Transaction
 */
function openTransactionModal(editMode = false) {
    document.getElementById('modalError').classList.add('hidden');
    document.getElementById('modalTitle').textContent = editMode ? 'Edit Transaction' : 'Add Transaction';
    document.getElementById('modalSubmitBtn').textContent = editMode ? 'Update' : 'Save Transaction';

    if (!editMode) {
        document.getElementById('txForm').reset();
        document.getElementById('txId').value = '';
        initDefaultDates();
    }

    document.getElementById('txModal').classList.remove('hidden');
}

function closeTransactionModal() {
    document.getElementById('txModal').classList.add('hidden');
}

async function editTransaction(id) {
    try {
        const response = await fetch(`${API_BASE}/${id}`);
        if (!response.ok) throw new Error('Failed to retrieve transaction');
        const tx = await response.json();

        document.getElementById('txId').value = tx.id;
        document.getElementById('txTitle').value = tx.title;
        document.getElementById('txAmount').value = tx.amount;
        document.getElementById('txType').value = tx.type;
        document.getElementById('txCategory').value = tx.category;
        document.getElementById('txDate').value = tx.transactionDate;
        document.getElementById('txNotes').value = tx.notes || '';

        openTransactionModal(true);
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

async function handleTransactionSubmit(event) {
    event.preventDefault();
    const id = document.getElementById('txId').value;
    const isEdit = Boolean(id);

    const payload = {
        title: document.getElementById('txTitle').value.trim(),
        amount: parseFloat(document.getElementById('txAmount').value),
        type: document.getElementById('txType').value,
        category: document.getElementById('txCategory').value,
        transactionDate: document.getElementById('txDate').value,
        notes: document.getElementById('txNotes').value.trim()
    };

    const url = isEdit ? `${API_BASE}/${id}` : API_BASE;
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const errData = await response.json();
            const msg = errData.message || (errData.errors ? Object.values(errData.errors).join(', ') : 'Failed to save');
            throw new Error(msg);
        }

        closeTransactionModal();
        showToast('Success', `Transaction ${isEdit ? 'updated' : 'created'} successfully!`, 'success');
        loadDashboard();

    } catch (err) {
        const errEl = document.getElementById('modalError');
        errEl.textContent = err.message;
        errEl.classList.remove('hidden');
    }
}

async function deleteTransaction(id) {
    if (!confirm('Are you sure you want to permanently delete this transaction?')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Failed to delete transaction');

        showToast('Deleted', 'Transaction removed.', 'info');
        loadDashboard();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

/**
 * Budget Modal & Logic
 */
async function openBudgetModal() {
    const today = new Date();
    const curMonth = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
    const budgetMonthInput = document.getElementById('budgetMonth');
    budgetMonthInput.value = curMonth;

    try {
        const response = await fetch(`${BUDGET_API}?monthYear=${curMonth}`);
        if (response.ok) {
            const data = await response.json();
            document.getElementById('budgetLimit').value = data.monthlyLimit || '';
        }
    } catch (err) {
        console.error(err);
    }

    document.getElementById('budgetModal').classList.remove('hidden');
}

function closeBudgetModal() {
    document.getElementById('budgetModal').classList.add('hidden');
}

async function handleBudgetSubmit(event) {
    event.preventDefault();
    const payload = {
        monthYear: document.getElementById('budgetMonth').value,
        monthlyLimit: parseFloat(document.getElementById('budgetLimit').value)
    };

    try {
        const response = await fetch(BUDGET_API, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error('Failed to save monthly budget');

        closeBudgetModal();
        showToast('Budget Saved', `Monthly budget updated to ${formatCurrency(payload.monthlyLimit)}.`, 'success');
        loadDashboard();
    } catch (err) {
        showToast('Error', err.message, 'error');
    }
}

/**
 * CSV Data Export
 */
function exportToCSV() {
    const params = getFilterParams();
    window.location.href = `${API_BASE}/export/csv?${params.toString()}`;
    showToast('Exporting', 'Generating your CSV spreadsheet download...', 'info');
}

/**
 * Toast Notifications
 */
function showToast(title, message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');

    const colorClasses = {
        success: 'bg-emerald-600 text-white',
        error: 'bg-rose-600 text-white',
        info: 'bg-slate-800 text-white'
    }[type] || 'bg-slate-800 text-white';

    toast.className = `${colorClasses} pointer-events-auto px-4 py-3 rounded-xl shadow-lg flex items-center space-x-3 text-xs font-medium animate-fade-in transition-all duration-300`;
    toast.innerHTML = `
        <div class="flex-1">
            <span class="font-bold">${escapeHtml(title)}:</span> ${escapeHtml(message)}
        </div>
        <button onclick="this.parentElement.remove()" class="text-white/80 hover:text-white ml-2">
            <i class="fa-solid fa-xmark"></i>
        </button>
    `;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

/**
 * Helpers
 */
function formatCurrency(amount) {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
    }).format(amount || 0);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

