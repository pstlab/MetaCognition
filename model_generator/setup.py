from setuptools import setup, find_packages

setup(
    name='model_generator',
    version='0.1.0',
    packages=find_packages(where='src'),
    package_dir={'': 'src'},
    install_requires=[
        'unified-planning>=1.3,<2',
        'up-tamer>=1.1',
        'up-fast-downward>=0.5',
    ],
)
