#include <stdio.h>
#include <iostream>
#include <iomanip>
#include <time.h>
#include <cstdlib>
#include <omp.h>

using namespace std;

#define SYSTEMTIME clock_t

 
void OnMult(int m_ar, int m_br) 
{
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i*m_br + j] = (double)(i + 1);

    Time1 = omp_get_wtime();

    #pragma omp parallel for private(temp, j, k)
    for(i = 0; i < m_ar; i++)
    {
        for(j = 0; j < m_br; j++)
        {
            temp = 0;
            for(k = 0; k < m_ar; k++)
            {    
                temp += pha[i*m_ar + k] * phb[k*m_br + j];
            }
            phc[i*m_ar + j] = temp;
        }
    }

    Time2 = omp_get_wtime();
    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
         (double)(Time2 - Time1));
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
    {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMult2(int m_ar, int m_br) 
{
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i*m_br + j] = (double)(i + 1);

    Time1 = omp_get_wtime();

    for(i = 0; i < m_ar; i++)
    {
        for(j = 0; j < m_br; j++)
        {
            temp = 0;
            #pragma omp parallel for reduction(+:temp)
            for(k = 0; k < m_ar; k++)
            {    
                temp += pha[i*m_ar + k] * phb[k*m_br + j];
            }
            phc[i*m_ar + j] = temp;
        }
    }

    Time2 = omp_get_wtime();
    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
         (double)(Time2 - Time1));
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
    {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}


// Line-by-line matrix multiplication
void OnMultLine(int m_ar, int m_br)
{
    // Setup
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_br) * sizeof(double));
    phc = (double *)malloc((m_ar * m_br) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i*m_br + j] = (double)(i + 1);

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_br; j++)
            phc[i*m_br + j] = 0.0;
    
    // Perform The calculation
    Time1 = omp_get_wtime();

    #pragma omp parallel for private(temp, j, k)
    for(i = 0; i < m_ar; i++)
    {
        for(k = 0; k < m_ar; k++)
        {
            temp = pha[i*m_ar + k];
            for(j = 0; j < m_br; j++)
            {
                phc[i*m_br + j] +=   temp * phb[k*m_br + j];
            }
        }
    }

    Time2 = omp_get_wtime();
    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
         (double)(Time2 - Time1));
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
    {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMultLineSimd(int m_ar, int m_br)
{
    // Setup
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_br) * sizeof(double));
    phc = (double *)malloc((m_ar * m_br) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i*m_br + j] = (double)(i + 1);

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_br; j++)
            phc[i*m_br + j] = 0.0;
    
    // Perform The calculation
    Time1 = omp_get_wtime();

    #pragma omp parallel for private(temp, j, k)
    for(i = 0; i < m_ar; i++)
    {
        for(k = 0; k < m_ar; k++)
        {
            temp = pha[i*m_ar + k];
            #pragma omp simd
            for(j = 0; j < m_br; j++)
            {
                phc[i*m_br + j] +=   temp * phb[k*m_br + j];
            }
        }
    }

    Time2 = omp_get_wtime();
    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
         (double)(Time2 - Time1));
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
    {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMultLineCollapse(int m_ar, int m_br)
{
    // Setup
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_br) * sizeof(double));
    phc = (double *)malloc((m_ar * m_br) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i*m_br + j] = (double)(i + 1);

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_br; j++)
            phc[i*m_br + j] = 0.0;
    
    // Perform The calculation
    Time1 = omp_get_wtime();

    #pragma omp parallel for  collapse(2) private(temp, j, k)
    for(i = 0; i < m_ar; i++)
    {
        for(k = 0; k < m_ar; k++)
        {
            temp = pha[i*m_ar + k];
            for(j = 0; j < m_br; j++)
            {
                phc[i*m_br + j] +=   temp * phb[k*m_br + j];
            }
        }
    }

    Time2 = omp_get_wtime();
    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
         (double)(Time2 - Time1));
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
    {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}

void OnMultLine2(int m_ar, int m_br)
{
    // Setup
    double Time1, Time2;
    
    char st[100];
    double temp;
    int i, j, k;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_br) * sizeof(double));
    phc = (double *)malloc((m_ar * m_br) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_br; i++)
        for(j = 0; j < m_br; j++)
            phb[i*m_br + j] = (double)(i + 1);

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_br; j++)
            phc[i*m_br + j] = 0.0;
    
    // Perform The calculation
    Time1 = omp_get_wtime();

    #pragma omp parallel private(i, j, k) shared(pha, phb, phc)
    for(i = 0; i < m_ar; i++) {
        for(k = 0; k < m_ar; k++)
        {
            temp = pha[i*m_ar + k];

            #pragma omp for private(j)
            for(j = 0; j < m_br; j++)
            {
                phc[i*m_br + j] +=   temp * phb[k*m_br + j];
            }
        }
    }

    Time2 = omp_get_wtime();
    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
         (double)(Time2 - Time1));
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
    {
        for(j = 0; j < min(10, m_br); j++)
            cout << phc[j] << " ";
    }
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}


void OnMultBlock(int m_ar, int m_br, int bkSize)
{
    SYSTEMTIME Time1, Time2;
    char st[100];

    int i, j, k, ii, jj, kk;

    double *pha, *phb, *phc;

    pha = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phb = (double *)malloc((m_ar * m_ar) * sizeof(double));
    phc = (double *)malloc((m_ar * m_ar) * sizeof(double));

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            pha[i*m_ar + j] = 1.0;

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            phb[i*m_ar + j] = (double)(i + 1);

    for(i = 0; i < m_ar; i++)
        for(j = 0; j < m_ar; j++)
            phc[i*m_ar + j] = 0.0;

    Time1 = clock();

    // Blocked multiplication
    for (ii = 0; ii < m_ar; ii += bkSize)
        for (kk = 0; kk < m_ar; kk += bkSize)
            for (jj = 0; jj < m_ar; jj += bkSize)

                for (i = ii; i < ii + bkSize && i < m_ar; i++)
                    for (k = kk; k < kk + bkSize && k < m_ar; k++)
                    {
                        double temp = pha[i*m_ar + k];
                        for (j = jj; j < jj + bkSize && j < m_ar; j++)
                        {
                            phc[i*m_ar + j] += temp * phb[k*m_ar + j];
                        }
                    }

    Time2 = clock();

    snprintf(st, sizeof(st), "Time: %3.3f seconds\n",
             (double)(Time2 - Time1) / CLOCKS_PER_SEC);
    cout << st;

    cout << "Result matrix: " << endl;
    for(i = 0; i < 1; i++)
        for(j = 0; j < min(10, m_ar); j++)
            cout << phc[j] << " ";
    cout << endl;

    free(pha);
    free(phb);
    free(phc);
}


int main(int argc, char *argv[])
{
    int lin, col, blockSize;
    int op;

    do {
        cout << endl << "1. Multiplication" << endl;
        cout << "4. Multiplication (parallel inner for)" << endl;
        cout << "2. Line Multiplication" << endl;
        cout << "5. Line Multiplication (parallel inner for)" << endl;
        cout << "6. Line Multiplication (simd)" << endl;
        cout << "7. Line Multiplication (collapsing)" << endl;
        cout << "3. Block Multiplication" << endl;
        cout << "0. Exit" << endl;
        cout << "Selection?: ";
        cin >> op;

        if (op == 0)
            break;

        cout << "Dimensions: lins=cols ? ";
        cin >> lin;
        col = lin;

        switch (op) {
            case 1:
                OnMult(lin, col);
                break;
            case 2:
                OnMultLine(lin, col);
                break;
            case 5:
                OnMultLine2(lin, col);
                break;
            case 6:
                OnMultLineSimd(lin, col);
                break;
            case 7:
                OnMultLineCollapse(lin, col);
                break;
            case 4:
                OnMult2(lin, col);
                break;
            case 3:
                cout << "Block Size? ";
                cin >> blockSize;
                OnMultBlock(lin, col, blockSize);
                break;
        }

    } while (op != 0);

    return 0;
}